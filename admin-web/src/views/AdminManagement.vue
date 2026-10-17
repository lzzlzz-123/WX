<script setup>
import { onMounted, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'

const records = ref([])
const loading = ref(false)
const createVisible = ref(false)
const createForm = ref({ username: '', password: '', realName: '', roleCode: 'OPERATOR' })
const saving = ref(false)

const roleMap = { SUPER_ADMIN: '超级管理员', OPERATOR: '运营', FINANCE: '财务' }

async function load() {
  loading.value = true
  try {
    const response = await adminApi.accounts()
    records.value = response.data || []
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  if (!createForm.value.username || createForm.value.password.length < 8) {
    ElMessage.warning('请填写用户名，密码至少 8 位')
    return
  }
  saving.value = true
  try {
    await adminApi.createAccount(createForm.value)
    createVisible.value = false
    createForm.value = { username: '', password: '', realName: '', roleCode: 'OPERATOR' }
    ElMessage.success('管理员已创建')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const target = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确认${target === 1 ? '启用' : '停用'}管理员 ${row.username}？`, '账号状态')
  try {
    await adminApi.changeAccountStatus(row.id, target)
    ElMessage.success('状态已更新')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function changeRole(row) {
  const { value } = await ElMessageBox.prompt('请输入新角色（SUPER_ADMIN / OPERATOR / FINANCE）', `调整角色：${row.username}`, {
    inputValue: row.roleCode,
    inputValidator: v => ['SUPER_ADMIN', 'OPERATOR', 'FINANCE'].includes((v || '').toUpperCase()) || '角色不合法'
  })
  try {
    await adminApi.updateAccount(row.id, { realName: row.realName, roleCode: value.toUpperCase() })
    ElMessage.success('角色已更新')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function resetPassword(row) {
  const { value } = await ElMessageBox.prompt('请输入新密码（至少 8 位）', `重置密码：${row.username}`, {
    inputType: 'password',
    inputValidator: v => (v && v.length >= 8) || '密码至少 8 位'
  })
  try {
    await adminApi.resetAccountPassword(row.id, { password: value })
    ElMessage.success('密码已重置')
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="page-heading">
    <div><p class="eyebrow">OPERATIONS CENTER</p><h1>账号权限</h1><p class="heading-note">管理端账号、角色与权限分配，仅超级管理员可操作。</p></div>
    <div class="heading-actions"><el-button type="primary" :icon="Plus" @click="createVisible = true">新增管理员</el-button></div>
  </section>

  <section class="module-panel">
    <div class="module-toolbar"><div class="module-search"><el-button :icon="Refresh" @click="load">刷新</el-button></div><span class="module-total">共 {{ records.length }} 个账号</span></div>
    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column prop="realName" label="姓名" min-width="120"><template #default="{ row }">{{ row.realName || '-' }}</template></el-table-column>
      <el-table-column label="角色" width="140"><template #default="{ row }"><el-tag :type="row.roleCode === 'SUPER_ADMIN' ? 'danger' : 'primary'" effect="plain">{{ roleMap[row.roleCode] || row.roleCode }}</el-tag></template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column prop="lastLoginAt" label="最近登录" width="170"><template #default="{ row }">{{ (row.lastLoginAt || '-').replace('T', ' ').slice(0, 16) }}</template></el-table-column>
      <el-table-column label="操作" width="200" align="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="changeRole(row)">角色</el-button>
          <el-button link type="primary" @click="resetPassword(row)">重置密码</el-button>
          <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新增管理员" width="440px">
      <el-form label-width="90px">
        <el-form-item label="用户名" required><el-input v-model="createForm.username" placeholder="登录用户名" /></el-form-item>
        <el-form-item label="初始密码" required><el-input v-model="createForm.password" type="password" placeholder="至少 8 位" show-password /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="createForm.realName" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.roleCode" style="width: 100%">
            <el-option label="运营" value="OPERATOR" />
            <el-option label="财务" value="FINANCE" />
            <el-option label="超级管理员" value="SUPER_ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </section>
</template>
