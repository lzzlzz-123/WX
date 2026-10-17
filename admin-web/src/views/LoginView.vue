<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '../api/admin'

const router = useRouter()
const loginLoading = ref(false)
const loginError = ref('')
const loginForm = ref({ username: 'admin', password: '' })

async function submitLogin() {
  loginError.value = ''
  const username = loginForm.value.username.trim()
  if (!username || !loginForm.value.password) {
    loginError.value = '请输入管理员账号和密码'
    return
  }
  loginLoading.value = true
  try {
    const result = await adminApi.login({ username, password: loginForm.value.password })
    if (result?.code !== 0) {
      throw new Error(result?.message || '登录失败，请检查账号和密码')
    }
    const token = result?.data?.token
    if (typeof token !== 'string' || !token.trim()) {
      throw new Error('登录响应缺少有效会话令牌')
    }
    localStorage.setItem('buildmart_token', token)
    loginForm.value.password = ''
    router.push('/overview')
  } catch (error) {
    loginError.value = error.message || '登录失败，请稍后重试'
  } finally {
    loginLoading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-panel">
      <div class="login-brand"><div class="brand-mark">筑</div><div><strong>筑云清松</strong><span>BUILD MART</span></div></div>
      <p class="login-eyebrow">OPERATIONS CENTER</p>
      <h1>登录运营后台</h1>
      <p class="login-subtitle">管理商品、订单与询价数据</p>
      <form class="login-form" @submit.prevent="submitLogin">
        <label>管理员账号<input v-model="loginForm.username" autocomplete="username" placeholder="请输入账号" /></label>
        <label>登录密码<input v-model="loginForm.password" type="password" autocomplete="current-password" placeholder="请输入密码" /></label>
        <p v-if="loginError" class="login-error">{{ loginError }}</p>
        <button class="login-submit" type="submit" :disabled="loginLoading">{{ loginLoading ? '登录中…' : '登录' }}</button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-page{min-height:100vh;display:grid;place-items:center;background:#f4f7fa;padding:24px}
.login-panel{width:min(420px,100%);padding:38px 40px 42px;background:#fff;border:1px solid #e3ebf0;border-radius:12px;box-shadow:0 16px 45px rgba(21,43,61,.09)}
.login-brand{display:flex;align-items:center;gap:12px;margin-bottom:44px}.login-brand>div:last-child{display:flex;flex-direction:column;line-height:1.2}.login-brand strong{font-size:18px;color:#152b3d}.login-brand span{font-size:9px;letter-spacing:2px;color:#91a2ad;margin-top:4px}.login-eyebrow{margin:0 0 10px;font-size:10px;letter-spacing:1.5px;color:#2e91ee}.login-panel h1{margin:0;color:#152b3d;font-size:26px}.login-subtitle{margin:9px 0 28px;color:#81919d;font-size:12px}.login-form{display:flex;flex-direction:column;gap:17px}.login-form label{display:flex;flex-direction:column;gap:7px;color:#496273;font-size:12px;font-weight:600}.login-form input{height:42px;border:1px solid #dce6ec;border-radius:7px;padding:0 12px;outline:none;color:#294556;font-size:13px}.login-form input:focus{border-color:#2e91ee;box-shadow:0 0 0 3px rgba(46,145,238,.12)}.login-error{margin:-4px 0 -3px;color:#c6544b;font-size:12px}.login-submit{height:42px;margin-top:3px;border:0;border-radius:7px;background:#2e91ee;color:#fff;font-size:13px;font-weight:600;cursor:pointer}.login-submit:hover{background:#2584dc}.login-submit:disabled{opacity:.65;cursor:wait}
</style>
