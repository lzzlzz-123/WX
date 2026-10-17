<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell, Document, Expand, Fold, Goods, House,
  MoreFilled, Setting, ShoppingCart, TrendCharts, User, Lock, Tickets
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)

const menuGroups = [
  { label: '工作台', items: [{ key: 'overview', label: '经营概览', icon: House }] },
  { label: '交易中心', items: [
    { key: 'orders', label: '订单管理', icon: ShoppingCart },
    { key: 'inquiries', label: '询价报价', icon: Document },
    { key: 'invoices', label: '开票管理', icon: Tickets }
  ] },
  { label: '商品与客户', items: [
    { key: 'products', label: '商品管理', icon: Goods },
    { key: 'customers', label: '客户管理', icon: User }
  ] },
  { label: '数据与设置', items: [
    { key: 'analytics', label: '数据统计', icon: TrendCharts },
    { key: 'settings', label: '系统设置', icon: Setting },
    { key: 'admins', label: '账号权限', icon: Lock }
  ] }
]

const activeMenu = computed(() => route.name || 'overview')
const activeLabel = computed(() => route.meta?.title || '经营概览')

function chooseMenu(key) {
  router.push({ name: key })
}

function logout() {
  localStorage.removeItem('buildmart_token')
  router.push('/login')
}

function handleAuthExpired() {
  localStorage.removeItem('buildmart_token')
  router.push('/login')
}

onMounted(() => window.addEventListener('buildmart-auth-expired', handleAuthExpired))
onUnmounted(() => window.removeEventListener('buildmart-auth-expired', handleAuthExpired))
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ collapsed }">
      <div class="brand">
        <div class="brand-mark">筑</div>
        <div v-if="!collapsed" class="brand-copy"><strong>筑云清松</strong><span>BUILD MART</span></div>
      </div>
      <nav class="side-nav">
        <section v-for="group in menuGroups" :key="group.label" class="nav-group">
          <p v-if="!collapsed" class="nav-caption">{{ group.label }}</p>
          <button v-for="item in group.items" :key="item.key" class="nav-item" :class="{ active: activeMenu === item.key }" @click="chooseMenu(item.key)">
            <el-icon><component :is="item.icon" /></el-icon>
            <span v-if="!collapsed">{{ item.label }}</span>
          </button>
        </section>
      </nav>
      <div class="sidebar-foot">
        <button class="nav-item" @click="collapsed = !collapsed"><el-icon><component :is="collapsed ? Expand : Fold" /></el-icon><span v-if="!collapsed">收起导航</span></button>
        <div v-if="!collapsed" class="support-card"><div class="support-icon"><el-icon><Bell /></el-icon></div><div><strong>需要帮助？</strong><span>联系客户成功团队</span></div><el-icon><MoreFilled /></el-icon></div>
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="crumb"><span>运营后台</span><i>/</i><strong>{{ activeLabel }}</strong></div>
        <div class="top-actions">
          <div class="top-divider"></div>
          <button class="profile profile-button" title="退出登录" @click="logout"><div class="avatar">管</div><div class="profile-copy"><strong>管理端用户</strong><span>退出登录</span></div><el-icon><MoreFilled /></el-icon></button>
        </div>
      </header>

      <div class="page-content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.profile-button{border:0;cursor:pointer;text-align:left}
</style>
