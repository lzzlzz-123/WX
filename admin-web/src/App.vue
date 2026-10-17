<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  Bell, Box, Calendar, DataAnalysis, Document, Expand, Fold, Goods,
  House, MoreFilled, Search, Setting, ShoppingCart, TrendCharts,
  User, WarningFilled
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from './api/admin'
import ProductManagement from './components/ProductManagement.vue'
import OrderManagement from './components/OrderManagement.vue'
import InquiryManagement from './components/InquiryManagement.vue'
import CustomerManagement from './components/CustomerManagement.vue'
import AnalyticsManagement from './components/AnalyticsManagement.vue'
import SettingManagement from './components/SettingManagement.vue'

const collapsed = ref(false)
const activeMenu = ref('overview')
const search = ref('')
const range = ref('近 7 天')
const showAllOrders = ref(false)
const dashboardLoading = ref(false)
const dashboardLoaded = ref(false)
const dashboardError = ref('')
const dashboardSummary = ref(null)
const trendPoints = ref([])
const trendLoading = ref(false)
const authenticated = ref(Boolean(localStorage.getItem('buildmart_token')))
const loginLoading = ref(false)
const loginError = ref('')
const loginForm = ref({ username: 'admin', password: '' })

const menuGroups = [
  { label: '工作台', items: [{ key: 'overview', label: '经营概览', icon: House }] },
  { label: '交易中心', items: [
    { key: 'orders', label: '订单管理', icon: ShoppingCart },
    { key: 'inquiries', label: '询价报价', icon: Document }
  ] },
  { label: '商品与客户', items: [
    { key: 'products', label: '商品管理', icon: Goods },
    { key: 'customers', label: '客户管理', icon: User }
  ] },
  { label: '数据与设置', items: [
    { key: 'analytics', label: '数据统计', icon: TrendCharts },
    { key: 'settings', label: '系统设置', icon: Setting }
  ] }
]

const orders = ref([])
const products = ref([])

const moduleComponents = {
  products: ProductManagement,
  orders: OrderManagement,
  inquiries: InquiryManagement,
  customers: CustomerManagement,
  analytics: AnalyticsManagement,
  settings: SettingManagement
}

const activeModule = computed(() => moduleComponents[activeMenu.value] || null)
const activeLabel = computed(() => menuGroups.flatMap(group => group.items).find(item => item.key === activeMenu.value)?.label || '经营概览')
const todayLabel = computed(() => new Intl.DateTimeFormat('en-US', {
  weekday: 'long', month: 'long', day: '2-digit', year: 'numeric'
}).format(new Date()).toUpperCase())

function formatCurrency(value) {
  const amount = Number(value)
  return Number.isFinite(amount)
    ? `¥ ${amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : '--'
}

const stats = computed(() => {
  const summary = dashboardSummary.value
  return [
    {
      label: '累计成交额',
      value: summary ? formatCurrency(summary.orderAmount) : '--',
      delta: summary ? `${summary.orderCount} 笔订单` : '--',
      note: summary ? '来自订单汇总' : '等待接口数据',
      icon: TrendCharts,
      tone: 'blue'
    },
    {
      label: '待发货订单',
      value: summary ? String(summary.pendingShipmentCount) : '--',
      delta: '--',
      note: '当前待处理',
      icon: ShoppingCart,
      tone: 'orange'
    },
    {
      label: '待响应询价',
      value: summary ? String(summary.pendingInquiryCount) : '--',
      delta: '--',
      note: '当前待报价',
      icon: Document,
      tone: 'green'
    },
    {
      label: '库存预警',
      value: summary ? String(summary.lowStockCount) : '--',
      delta: '--',
      note: '库存不高于安全线',
      icon: WarningFilled,
      tone: 'red'
    }
  ]
})

const todoItems = computed(() => {
  const summary = dashboardSummary.value
  if (!summary) {
    return [
      { title: '待发货订单', detail: '等待订单统计数据', tone: 'orange', target: 'orders' },
      { title: '询价报价待响应', detail: '等待询价统计数据', tone: 'red', target: 'inquiries' },
      { title: '库存预警', detail: '等待商品统计数据', tone: 'blue', target: 'products' }
    ]
  }
  return [
    { title: '待发货订单', detail: `${summary.pendingShipmentCount} 笔订单等待安排配送`, tone: 'orange', target: 'orders' },
    { title: '询价报价待响应', detail: `${summary.pendingInquiryCount} 个询价等待报价`, tone: 'red', target: 'inquiries' },
    { title: '库存预警', detail: `${summary.lowStockCount} 个商品低于安全库存`, tone: 'blue', target: 'products' }
  ]
})

const visibleOrders = computed(() => showAllOrders.value ? orders.value : orders.value.slice(0, 4))
const trendBars = computed(() => {
  const values = trendPoints.value.map(point => Number(point.salesAmount || 0))
  const peak = Math.max(...values, 0)
  return trendPoints.value.map(point => {
    const value = Number(point.salesAmount || 0)
    return {
      bucket: point.bucket,
      value,
      height: peak > 0 ? Math.max(value / peak * 100, value > 0 ? 4 : 0) : 0,
      shortLabel: point.bucket.length > 5 ? point.bucket.slice(5) : point.bucket
    }
  })
})
const filteredProducts = computed(() => {
  if (!search.value.trim()) return products.value
  const keyword = search.value.toLowerCase()
  return products.value.filter(item => `${item.name}${item.sku}`.toLowerCase().includes(keyword))
})

async function loadTrend() {
  trendLoading.value = true
  try {
    const periods = range.value === '近 7 天' ? 7 : 30
    const response = await adminApi.statisticsTrend({ dimension: 'day', periods })
    trendPoints.value = response.data?.points || []
  } catch {
    trendPoints.value = []
  } finally {
    trendLoading.value = false
  }
}

async function loadLiveData() {
  dashboardLoading.value = true
  dashboardLoaded.value = false
  dashboardError.value = ''
  dashboardSummary.value = null
  orders.value = []
  products.value = []
  try {
    const [summaryResult, productResult, orderResult] = await Promise.allSettled([
      adminApi.dashboardSummary(),
      adminApi.products({ page: 1, size: 20, status: 1 }),
      adminApi.orders({ page: 1, size: 10 })
    ])
    loadTrend()
    const errors = []

    if (summaryResult.status === 'fulfilled') {
      dashboardSummary.value = summaryResult.value?.data || null
    } else {
      errors.push(`概览：${summaryResult.reason?.message || '请求失败'}`)
    }

    if (productResult.status === 'fulfilled') {
      const productRecords = productResult.value?.data?.records || []
      products.value = productRecords.map(item => ({
        name: item.name,
        sku: item.sku,
        unit: item.unit,
        stock: Number(item.stock || 0).toLocaleString('zh-CN'),
        stockLevel: Number(item.stock || 0) <= 30 ? 'danger' : Number(item.stock || 0) <= 100 ? 'warning' : 'good',
        price: `¥ ${item.basePrice} / ${item.unit}`,
        status: item.status === 1 ? '在售' : '已下架'
      }))
    } else {
      errors.push(`商品：${productResult.reason?.message || '请求失败'}`)
    }

    if (orderResult.status === 'fulfilled') {
      const orderRecords = orderResult.value?.data?.records || []
      const statusMap = {
        PENDING_PAYMENT: ['待付款', 'info'],
        PENDING_SHIPMENT: ['待发货', 'warning'],
        PENDING_RECEIPT: ['配送中', 'success'],
        COMPLETED: ['已完成', 'success'],
        REFUNDING: ['售后中', 'danger'],
        CANCELLED: ['已取消', 'info']
      }
      orders.value = orderRecords.map(item => {
        const [status, statusType] = statusMap[item.status] || [item.status || '未知', 'info']
        return {
          no: item.orderNo,
          company: item.receiverName || '待补充收货信息',
          product: '订单商品明细',
          amount: formatCurrency(item.payableAmount),
          status,
          statusType,
          time: item.createdAt?.slice(11, 16) || '-'
        }
      })
    } else {
      errors.push(`订单：${orderResult.reason?.message || '请求失败'}`)
    }

    dashboardError.value = errors.join('；')
  } catch (error) {
    dashboardError.value = error.message || '业务数据加载失败'
  } finally {
    dashboardLoaded.value = true
    dashboardLoading.value = false
  }
}

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
    authenticated.value = true
    loginForm.value.password = ''
    await loadLiveData()
  } catch (error) {
    loginError.value = error.message || '登录失败，请稍后重试'
  } finally {
    loginLoading.value = false
  }
}

function logout() {
  localStorage.removeItem('buildmart_token')
  authenticated.value = false
  dashboardSummary.value = null
  orders.value = []
  products.value = []
}

function handleAuthExpired() {
  authenticated.value = false
  loginError.value = '登录已失效，请重新登录'
}

onMounted(() => {
  window.addEventListener('buildmart-auth-expired', handleAuthExpired)
  if (authenticated.value) loadLiveData()
})

onUnmounted(() => window.removeEventListener('buildmart-auth-expired', handleAuthExpired))

function chooseMenu(key) {
  activeMenu.value = key
  search.value = ''
}

function quickAction(label, target) {
  if (target) {
    chooseMenu(target)
    return
  }
  ElMessage.info(`${label}暂未接入业务接口`)
}
</script>

<template>
  <div v-if="!authenticated" class="login-page">
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
  <div v-else class="app-shell">
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
          <div class="top-search"><el-icon><Search /></el-icon><input v-model="search" placeholder="搜索商品、订单号、客户" /><kbd>⌘ K</kbd></div>
          <button class="icon-button" title="通知"><el-icon><Bell /></el-icon></button>
          <div class="top-divider"></div>
          <button class="profile profile-button" title="退出登录" @click="logout"><div class="avatar">管</div><div class="profile-copy"><strong>管理端用户</strong><span>退出登录</span></div><el-icon><MoreFilled /></el-icon></button>
        </div>
      </header>

      <div class="page-content">
        <section class="page-heading">
          <div>
            <p class="eyebrow">{{ activeMenu === 'overview' ? todayLabel : 'OPERATIONS CENTER' }}</p>
            <h1>{{ activeMenu === 'overview' ? '经营概览' : activeLabel }}</h1>
            <p class="heading-note">{{ activeMenu === 'overview' ? '实时查看商品、订单和询价数据。' : `${activeLabel}业务数据与操作` }}</p>
          </div>
          <div v-if="activeMenu === 'overview'" class="heading-actions">
            <el-button plain :icon="Calendar" @click="quickAction('报表导出')">导出报表</el-button>
            <el-button type="primary" :icon="Box" @click="chooseMenu('products')">新建商品</el-button>
          </div>
        </section>

        <template v-if="activeMenu === 'overview'">
          <div v-if="dashboardError" class="dashboard-alert">
            <span>部分业务数据加载失败：{{ dashboardError }}</span>
            <el-button link type="danger" :loading="dashboardLoading" @click="loadLiveData">重试</el-button>
          </div>

          <section class="stat-grid">
            <article v-for="stat in stats" :key="stat.label" class="stat-card"><div class="stat-top"><div class="stat-icon" :class="stat.tone"><el-icon><component :is="stat.icon" /></el-icon></div><span class="stat-period">实时 <el-icon><MoreFilled /></el-icon></span></div><p>{{ stat.label }}</p><strong>{{ stat.value }}</strong><div class="stat-foot"><span :class="stat.tone">{{ stat.delta }}</span><small>{{ stat.note }}</small></div></article>
          </section>

          <section class="primary-grid">
            <article class="panel sales-panel">
              <div class="panel-head"><div><h2>销售概览</h2><p>按天统计的成交额走势</p></div><el-select v-model="range" size="small" style="width: 104px" @change="loadTrend"><el-option label="近 7 天" value="近 7 天" /><el-option label="近 30 天" value="近 30 天" /></el-select></div>
              <div class="chart-wrap"><div v-if="trendLoading" class="dashboard-state">正在加载趋势数据…</div><div v-else-if="!trendBars.length" class="dashboard-state">暂无趋势数据</div><div v-else class="trend-chart"><div v-for="bar in trendBars" :key="bar.bucket" class="trend-col" :title="`${bar.bucket}：${formatCurrency(bar.value)}`"><div class="trend-bar" :style="{ height: `${bar.height}%` }"></div><span class="trend-label">{{ bar.shortLabel }}</span></div></div></div>
              <div class="chart-legend"><span><i class="dot blue"></i>成交额 <strong>{{ dashboardSummary ? formatCurrency(dashboardSummary.orderAmount) : '--' }}</strong></span><span><i class="dot green"></i>订单量 <strong>{{ dashboardSummary ? `${dashboardSummary.orderCount} 笔` : '--' }}</strong></span></div>
            </article>
            <article class="panel focus-panel"><div class="panel-head"><div><h2>当前待办</h2><p>根据实时业务数据汇总</p></div><el-button link type="primary" @click="loadLiveData">刷新</el-button></div><div class="task-list"><button v-for="task in todoItems" :key="task.title" class="task-item" @click="quickAction(task.title, task.target)"><span class="task-dot" :class="task.tone"></span><span class="task-copy"><strong>{{ task.title }}</strong><small>{{ task.detail }}</small></span><el-icon><MoreFilled /></el-icon></button></div><div class="focus-foot"><div class="progress-label"><span>数据同步状态</span><strong>{{ dashboardLoading ? '加载中' : dashboardLoaded ? '已同步' : '--' }}</strong></div><div class="progress"><i :style="{ width: dashboardLoading ? '35%' : dashboardLoaded ? '100%' : '0%' }"></i></div></div></article>
          </section>

          <section class="panel table-panel"><div class="panel-head"><div><h2>最近订单</h2><p>实时跟进近期订单状态</p></div><div class="panel-actions"><el-button link type="primary" @click="chooseMenu('orders')">订单管理</el-button><el-button link type="primary" :loading="dashboardLoading" @click="loadLiveData">刷新</el-button></div></div><el-table v-loading="dashboardLoading" :data="visibleOrders" style="width: 100%" class="order-table" empty-text="暂无订单数据"><el-table-column label="订单信息" min-width="260"><template #default="{ row }"><div class="order-info"><strong>{{ row.no }}</strong><span>{{ row.company }}</span></div></template></el-table-column><el-table-column label="采购商品" min-width="210"><template #default="{ row }"><span class="muted">{{ row.product }}</span></template></el-table-column><el-table-column label="订单金额" width="140"><template #default="{ row }"><strong class="amount">{{ row.amount }}</strong></template></el-table-column><el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="row.statusType" effect="light" round>{{ row.status }}</el-tag></template></el-table-column><el-table-column label="下单时间" width="120"><template #default="{ row }"><span class="muted">{{ row.time }}</span></template></el-table-column><el-table-column label="操作" width="92" align="right"><template #default="{ row }"><el-button link type="primary" @click="quickAction(`订单 ${row.no}`, 'orders')">查看</el-button></template></el-table-column></el-table></section>

          <section class="lower-grid"><article class="panel product-panel"><div class="panel-head"><div><h2>库存概况</h2><p>重点商品库存健康度</p></div><el-button link type="primary" @click="chooseMenu('products')">商品管理</el-button></div><div v-if="dashboardLoading" class="dashboard-state product-state">正在加载商品数据</div><div v-else-if="!filteredProducts.length" class="dashboard-state product-state">暂无商品数据</div><div v-else class="product-list"><div v-for="product in filteredProducts" :key="product.sku" class="product-row"><div class="product-symbol"><el-icon><Goods /></el-icon></div><div class="product-main"><strong>{{ product.name }}</strong><span>{{ product.sku }} · {{ product.unit }}</span></div><div class="product-price">{{ product.price }}</div><div class="product-stock"><span :class="product.stockLevel">{{ product.stock }}</span><small>库存</small></div><el-tag :type="product.status === '在售' ? 'success' : 'info'" effect="plain">{{ product.status }}</el-tag></div></div></article><article class="panel quick-panel"><div class="panel-head"><div><h2>快捷入口</h2><p>进入真实业务模块</p></div></div><div class="quick-actions"><button @click="chooseMenu('products')"><span class="quick-icon blue"><el-icon><Goods /></el-icon></span><span>商品管理</span><el-icon><MoreFilled /></el-icon></button><button @click="chooseMenu('inquiries')"><span class="quick-icon orange"><el-icon><Document /></el-icon></span><span>询价报价</span><el-icon><MoreFilled /></el-icon></button><button @click="chooseMenu('orders')"><span class="quick-icon green"><el-icon><ShoppingCart /></el-icon></span><span>订单管理</span><el-icon><MoreFilled /></el-icon></button><button @click="quickAction('数据报表')"><span class="quick-icon violet"><el-icon><DataAnalysis /></el-icon></span><span>数据报表</span><el-icon><MoreFilled /></el-icon></button></div></article></section>
        </template>

        <component v-else-if="activeModule" :is="activeModule" />
        <section v-else class="panel module-placeholder"><h2>{{ activeLabel }}</h2><p>该模块尚未接入后端接口。</p></section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login-page{min-height:100vh;display:grid;place-items:center;background:#f4f7fa;padding:24px}
.login-panel{width:min(420px,100%);padding:38px 40px 42px;background:#fff;border:1px solid #e3ebf0;border-radius:12px;box-shadow:0 16px 45px rgba(21,43,61,.09)}
.login-brand{display:flex;align-items:center;gap:12px;margin-bottom:44px}.login-brand>div:last-child{display:flex;flex-direction:column;line-height:1.2}.login-brand strong{font-size:18px;color:#152b3d}.login-brand span{font-size:9px;letter-spacing:2px;color:#91a2ad;margin-top:4px}.login-eyebrow{margin:0 0 10px;font-size:10px;letter-spacing:1.5px;color:#2e91ee}.login-panel h1{margin:0;color:#152b3d;font-size:26px}.login-subtitle{margin:9px 0 28px;color:#81919d;font-size:12px}.login-form{display:flex;flex-direction:column;gap:17px}.login-form label{display:flex;flex-direction:column;gap:7px;color:#496273;font-size:12px;font-weight:600}.login-form input{height:42px;border:1px solid #dce6ec;border-radius:7px;padding:0 12px;outline:none;color:#294556;font-size:13px}.login-form input:focus{border-color:#2e91ee;box-shadow:0 0 0 3px rgba(46,145,238,.12)}.login-error{margin:-4px 0 -3px;color:#c6544b;font-size:12px}.login-submit{height:42px;margin-top:3px;border:0;border-radius:7px;background:#2e91ee;color:#fff;font-size:13px;font-weight:600;cursor:pointer}.login-submit:hover{background:#2584dc}.login-submit:disabled{opacity:.65;cursor:wait}.profile-button{border:0;cursor:pointer;text-align:left}
.dashboard-alert{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:-10px 0 18px;padding:10px 14px;border:1px solid #f1c9c2;border-radius:8px;background:#fff7f5;color:#a44e3e;font-size:12px}
.dashboard-state{display:grid;place-items:center;min-height:150px;color:#9aa8b1;font-size:12px;text-align:center}
.product-state{min-height:180px}
.panel-actions{display:flex;align-items:center;gap:4px}
.module-placeholder{min-height:260px;display:grid;place-items:center;align-content:center;gap:8px;text-align:center;color:#8c9ca6}
.module-placeholder h2{margin:0;color:#304b5d;font-size:16px}
.module-placeholder p{margin:0;font-size:12px}
.trend-chart{display:flex;align-items:stretch;gap:4px;height:100%;min-height:190px;padding:6px 4px 0}
.trend-col{flex:1;min-width:0;display:flex;flex-direction:column;align-items:center;justify-content:flex-end}
.trend-bar{width:70%;border-radius:5px 5px 0 0;background:linear-gradient(180deg,#4ba3f2,#2e91ee);transition:height .3s ease}
.trend-col:hover .trend-bar{filter:brightness(1.1)}
.trend-label{font-size:9px;color:#9aa8b1;margin-top:7px;white-space:nowrap}

</style>
