<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'

const overview = ref(null)
const overviewLoading = ref(false)
const trendLoading = ref(false)
const dimension = ref('day')
const metric = ref('salesAmount')
const points = ref([])

const dimensionOptions = [
  { label: '近 30 天', value: 'day', periods: 30 },
  { label: '近 12 周', value: 'week', periods: 12 },
  { label: '近 12 月', value: 'month', periods: 12 }
]

const metricOptions = [
  { label: '销售额', value: 'salesAmount', unit: '元', tone: 'blue' },
  { label: '订单量', value: 'orderCount', unit: '笔', tone: 'green' },
  { label: '新增客户', value: 'newCustomerCount', unit: '人', tone: 'violet' }
]

function formatCurrency(value) {
  const amount = Number(value)
  return Number.isFinite(amount)
    ? `¥ ${amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : '--'
}

function formatMetric(value) {
  const option = metricOptions.find(item => item.value === metric.value)
  const num = Number(value)
  if (!Number.isFinite(num)) return '--'
  return metric.value === 'salesAmount'
    ? formatCurrency(num)
    : `${num.toLocaleString('zh-CN')} ${option.unit}`
}

const cards = computed(() => {
  const data = overview.value
  return [
    { label: '累计销售额', value: data ? formatCurrency(data.totalSalesAmount) : '--', note: data ? `今日 ${formatCurrency(data.todaySalesAmount)}` : '等待数据', tone: 'blue' },
    { label: '累计订单', value: data ? data.totalOrderCount.toLocaleString('zh-CN') : '--', note: data ? `今日 ${data.todayOrderCount} 笔` : '等待数据', tone: 'green' },
    { label: '累计客户', value: data ? data.totalCustomerCount.toLocaleString('zh-CN') : '--', note: data ? `今日新增 ${data.todayNewCustomerCount}` : '等待数据', tone: 'violet' },
    { label: '待发货 / 待支付', value: data ? `${data.pendingShipmentCount} / ${data.pendingPaymentCount}` : '--', note: '当前待处理订单', tone: 'orange' },
    { label: '在售商品', value: data ? data.activeProductCount.toLocaleString('zh-CN') : '--', note: data ? `低库存 ${data.lowStockCount} 个` : '等待数据', tone: 'teal' },
    { label: '待报价询价', value: data ? String(data.pendingInquiryCount) : '--', note: '等待响应', tone: 'red' }
  ]
})

const maxValue = computed(() => {
  const values = points.value.map(point => Number(point[metric.value] || 0))
  return Math.max(...values, 0)
})

const bars = computed(() => {
  const peak = maxValue.value
  return points.value.map(point => {
    const value = Number(point[metric.value] || 0)
    return {
      bucket: point.bucket,
      value,
      height: peak > 0 ? Math.max(value / peak * 100, value > 0 ? 3 : 0) : 0,
      shortLabel: point.bucket.length > 5 ? point.bucket.slice(5) : point.bucket
    }
  })
})

const metricSummary = computed(() => {
  const sum = points.value.reduce((acc, point) => acc + Number(point[metric.value] || 0), 0)
  const peak = maxValue.value
  const option = metricOptions.find(item => item.value === metric.value)
  return { sum, peak, unit: option.unit }
})

async function loadOverview() {
  overviewLoading.value = true
  try {
    const response = await adminApi.statisticsOverview()
    overview.value = response.data
  } catch (error) {
    ElMessage.error(`总览加载失败：${error.message}`)
  } finally {
    overviewLoading.value = false
  }
}

async function loadTrend() {
  trendLoading.value = true
  try {
    const option = dimensionOptions.find(item => item.value === dimension.value)
    const response = await adminApi.statisticsTrend({ dimension: dimension.value, periods: option.periods })
    points.value = response.data?.points || []
  } catch (error) {
    ElMessage.error(`趋势加载失败：${error.message}`)
  } finally {
    trendLoading.value = false
  }
}

function changeDimension() {
  loadTrend()
}

function refresh() {
  loadOverview()
  loadTrend()
}

onMounted(refresh)
</script>

<template>
  <section class="analytics">
    <div class="overview-grid" v-loading="overviewLoading">
      <article v-for="card in cards" :key="card.label" class="overview-card">
        <span class="card-dot" :class="card.tone"></span>
        <p>{{ card.label }}</p>
        <strong>{{ card.value }}</strong>
        <small>{{ card.note }}</small>
      </article>
    </div>

    <section class="trend-panel">
      <div class="trend-head">
        <div>
          <h2>经营趋势</h2>
          <p>当前区间{{ metricOptions.find(m => m.value === metric).label }}合计 <strong>{{ metric === 'salesAmount' ? formatCurrency(metricSummary.sum) : `${metricSummary.sum.toLocaleString('zh-CN')} ${metricSummary.unit}` }}</strong>，单期峰值 <strong>{{ metric === 'salesAmount' ? formatCurrency(metricSummary.peak) : `${metricSummary.peak.toLocaleString('zh-CN')} ${metricSummary.unit}` }}</strong></p>
        </div>
        <div class="trend-actions">
          <div class="metric-tabs">
            <button v-for="option in metricOptions" :key="option.value" :class="{ active: metric === option.value }" @click="metric = option.value">{{ option.label }}</button>
          </div>
          <el-select v-model="dimension" style="width: 120px" @change="changeDimension">
            <el-option v-for="option in dimensionOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <el-button :icon="Refresh" :loading="trendLoading" @click="refresh">刷新</el-button>
        </div>
      </div>

      <div v-if="trendLoading" class="trend-state">正在加载趋势数据…</div>
      <div v-else-if="!points.length" class="trend-state">暂无趋势数据</div>
      <div v-else class="chart">
        <div v-for="bar in bars" :key="bar.bucket" class="chart-col" :title="`${bar.bucket}：${formatMetric(bar.value)}`">
          <span class="chart-value">{{ bar.value > 0 ? formatMetric(bar.value) : '' }}</span>
          <div class="chart-bar" :class="metric" :style="{ height: `${bar.height}%` }"></div>
          <span class="chart-label">{{ bar.shortLabel }}</span>
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped>
.analytics{display:flex;flex-direction:column;gap:18px}
.overview-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;min-height:96px}
.overview-card{position:relative;background:#fff;border:1px solid #e3ebf0;border-radius:12px;padding:18px 20px;display:flex;flex-direction:column;gap:6px}
.overview-card p{margin:0;font-size:12px;color:#81919d}
.overview-card strong{font-size:22px;color:#152b3d}
.overview-card small{font-size:11px;color:#91a2ad}
.card-dot{position:absolute;top:18px;right:18px;width:9px;height:9px;border-radius:50%}
.card-dot.blue{background:#2e91ee}.card-dot.green{background:#3fae7a}.card-dot.violet{background:#8b6fd8}
.card-dot.orange{background:#e8934c}.card-dot.teal{background:#3aa6a0}.card-dot.red{background:#e0705c}
.trend-panel{background:#fff;border:1px solid #e3ebf0;border-radius:12px;padding:20px 22px}
.trend-head{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;flex-wrap:wrap}
.trend-head h2{margin:0;font-size:15px;color:#152b3d}
.trend-head p{margin:6px 0 0;font-size:12px;color:#81919d}
.trend-head p strong{color:#2e91ee}
.trend-actions{display:flex;gap:10px;align-items:center}
.metric-tabs{display:flex;background:#eef3f7;border-radius:8px;padding:3px}
.metric-tabs button{border:0;background:none;padding:5px 14px;font-size:12px;color:#617d8c;border-radius:6px;cursor:pointer}
.metric-tabs button.active{background:#fff;color:#2e91ee;font-weight:600;box-shadow:0 1px 4px rgba(21,43,61,.1)}
.trend-state{display:grid;place-items:center;min-height:240px;color:#9aa8b1;font-size:12px}
.chart{display:flex;align-items:stretch;gap:4px;height:280px;margin-top:20px;padding-top:24px}
.chart-col{flex:1;min-width:0;display:flex;flex-direction:column;align-items:center;justify-content:flex-end;position:relative}
.chart-value{font-size:10px;color:#81919d;white-space:nowrap;margin-bottom:4px;opacity:0;transition:opacity .15s}
.chart-col:hover .chart-value{opacity:1}
.chart-bar{width:70%;min-height:0;border-radius:5px 5px 0 0;transition:height .3s ease}
.chart-bar.salesAmount{background:linear-gradient(180deg,#4ba3f2,#2e91ee)}
.chart-bar.orderCount{background:linear-gradient(180deg,#55c18e,#3fae7a)}
.chart-bar.newCustomerCount{background:linear-gradient(180deg,#a086e3,#8b6fd8)}
.chart-col:hover .chart-bar{filter:brightness(1.08)}
.chart-label{font-size:10px;color:#9aa8b1;margin-top:8px;white-space:nowrap}
</style>
