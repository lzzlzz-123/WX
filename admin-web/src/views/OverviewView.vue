<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Box, Calendar, DataAnalysis, Document, Goods, MoreFilled,
  ShoppingCart, TrendCharts, WarningFilled
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'

const router = useRouter()
const search = ref('')
const range = ref('近 7 天')
const showAllOrders = ref(false)
const dashboardLoading = ref(false)
const dashboardLoaded = ref(false)
const dashboardError = ref('')
const dashboardSummary = ref(null)
const trendPoints = ref([])
const trendLoading = ref(false)
const orders = ref([])
const products = ref([])

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
    { label: '累计成交额', value: summary ? formatCurrency(summary.orderAmount) : '--', delta: summary ? `${summary.orderCount} 笔订单` : '--', note: summary ? '来自订单汇总' : '等待接口数据', icon: TrendCharts, tone: 'blue' },
    { label: '待发货订单', value: summary ? String(summary.pendingShipmentCount) : '--', delta: '--', note: '当前待处理', icon: ShoppingCart, tone: 'orange' },
    { label: '待响应询价', value: summary ? String(summary.pendingInquiryCount) : '--', delta: '--', note: '当前待报价', icon: Document, tone: 'green' },
    { label: '库存预警', value: summary ? String(summary.lowStockCount) : '--', delta: '--', note: '库存不高于安全线', icon: WarningFilled, tone: 'red' }
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
        PENDING_RECEIPT: ['待收货', 'success'],
        COMPLETED: ['已完成', 'success'],
        REFUNDING: ['售后中', 'danger'],
        REFUNDED: ['已退款', 'danger'],
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

function go(name) {
  router.push({ name })
}

function quickAction(label, target) {
  if (target) {
    go(target)
    return
  }
  ElMessage.info(`${label}暂未接入业务接口`)
}

onMounted(loadLiveData)
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">{{ todayLabel }}</p>
      <h1>经营概览</h1>
      <p class="heading-note">实时查看商品、订单和询价数据。</p>
    </div>
    <div class="heading-actions">
      <el-button plain :icon="Calendar" @click="quickAction('报表导出')">导出报表</el-button>
      <el-button type="primary" :icon="Box" @click="go('products')">新建商品</el-button>
    </div>
  </section>

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

  <section class="panel table-panel"><div class="panel-head"><div><h2>最近订单</h2><p>实时跟进近期订单状态</p></div><div class="panel-actions"><el-button link type="primary" @click="go('orders')">订单管理</el-button><el-button link type="primary" :loading="dashboardLoading" @click="loadLiveData">刷新</el-button></div></div><el-table v-loading="dashboardLoading" :data="visibleOrders" style="width: 100%" class="order-table" empty-text="暂无订单数据"><el-table-column label="订单信息" min-width="260"><template #default="{ row }"><div class="order-info"><strong>{{ row.no }}</strong><span>{{ row.company }}</span></div></template></el-table-column><el-table-column label="采购商品" min-width="210"><template #default="{ row }"><span class="muted">{{ row.product }}</span></template></el-table-column><el-table-column label="订单金额" width="140"><template #default="{ row }"><strong class="amount">{{ row.amount }}</strong></template></el-table-column><el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="row.statusType" effect="light" round>{{ row.status }}</el-tag></template></el-table-column><el-table-column label="下单时间" width="120"><template #default="{ row }"><span class="muted">{{ row.time }}</span></template></el-table-column><el-table-column label="操作" width="92" align="right"><template #default="{ row }"><el-button link type="primary" @click="quickAction(`订单 ${row.no}`, 'orders')">查看</el-button></template></el-table-column></el-table></section>

  <section class="lower-grid"><article class="panel product-panel"><div class="panel-head"><div><h2>库存概况</h2><p>重点商品库存健康度</p></div><el-button link type="primary" @click="go('products')">商品管理</el-button></div><div v-if="dashboardLoading" class="dashboard-state product-state">正在加载商品数据</div><div v-else-if="!filteredProducts.length" class="dashboard-state product-state">暂无商品数据</div><div v-else class="product-list"><div v-for="product in filteredProducts" :key="product.sku" class="product-row"><div class="product-symbol"><el-icon><Goods /></el-icon></div><div class="product-main"><strong>{{ product.name }}</strong><span>{{ product.sku }} · {{ product.unit }}</span></div><div class="product-price">{{ product.price }}</div><div class="product-stock"><span :class="product.stockLevel">{{ product.stock }}</span><small>库存</small></div><el-tag :type="product.status === '在售' ? 'success' : 'info'" effect="plain">{{ product.status }}</el-tag></div></div></article><article class="panel quick-panel"><div class="panel-head"><div><h2>快捷入口</h2><p>进入真实业务模块</p></div></div><div class="quick-actions"><button @click="go('products')"><span class="quick-icon blue"><el-icon><Goods /></el-icon></span><span>商品管理</span><el-icon><MoreFilled /></el-icon></button><button @click="go('inquiries')"><span class="quick-icon orange"><el-icon><Document /></el-icon></span><span>询价报价</span><el-icon><MoreFilled /></el-icon></button><button @click="go('orders')"><span class="quick-icon green"><el-icon><ShoppingCart /></el-icon></span><span>订单管理</span><el-icon><MoreFilled /></el-icon></button><button @click="quickAction('数据报表')"><span class="quick-icon violet"><el-icon><DataAnalysis /></el-icon></span><span>数据报表</span><el-icon><MoreFilled /></el-icon></button></div></article></section>
</template>

<style scoped>
.dashboard-alert{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:-10px 0 18px;padding:10px 14px;border:1px solid #f1c9c2;border-radius:8px;background:#fff7f5;color:#a44e3e;font-size:12px}
.dashboard-state{display:grid;place-items:center;min-height:150px;color:#9aa8b1;font-size:12px;text-align:center}
.product-state{min-height:180px}
.panel-actions{display:flex;align-items:center;gap:4px}
.trend-chart{display:flex;align-items:stretch;gap:4px;height:100%;min-height:190px;padding:6px 4px 0}
.trend-col{flex:1;min-width:0;display:flex;flex-direction:column;align-items:center;justify-content:flex-end}
.trend-bar{width:70%;border-radius:5px 5px 0 0;background:linear-gradient(180deg,#4ba3f2,#2e91ee);transition:height .3s ease}
.trend-col:hover .trend-bar{filter:brightness(1.1)}
.trend-label{font-size:9px;color:#9aa8b0;margin-top:7px;white-space:nowrap}
</style>
