<script setup>
import { onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'

const records = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const keyword = ref('')
const status = ref(null)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

function formatCurrency(value) {
  const amount = Number(value)
  return Number.isFinite(amount)
    ? `¥ ${amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : '¥ 0.00'
}

async function load() {
  loading.value = true
  try {
    const response = await adminApi.customers({
      page: page.value,
      size: 20,
      keyword: keyword.value?.trim() || undefined,
      status: status.value === null || status.value === '' ? undefined : status.value
    })
    records.value = response.data.records
    total.value = response.data.total
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const response = await adminApi.customer(row.id)
    detail.value = response.data
  } catch (error) {
    ElMessage.error(error.message)
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function toggleStatus(row) {
  const target = row.status === 1 ? 0 : 1
  const action = target === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      target === 0
        ? `禁用后客户「${row.nickname}」将无法登录小程序，确认继续？`
        : `确认恢复客户「${row.nickname}」的登录权限？`,
      `${action}客户`
    )
  } catch {
    return
  }
  try {
    await adminApi.changeCustomerStatus(row.id, target)
    ElMessage.success(`已${action}`)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function editCredit(row) {
  let value
  try {
    const result = await ElMessageBox.prompt('请输入新的信用额度（元）', '调整信用额度', {
      inputValue: String(row.creditLimit ?? 0),
      inputPattern: /^\d+(\.\d{1,2})?$/,
      inputErrorMessage: '额度必须是不小于 0 的数字'
    })
    value = result.value
  } catch {
    return
  }
  try {
    await adminApi.changeCustomerCredit(row.id, value)
    ElMessage.success('信用额度已更新')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="customer-panel">
    <div class="customer-toolbar">
      <div class="customer-filters">
        <el-input v-model="keyword" placeholder="昵称 / 手机号 / 公司名" clearable :prefix-icon="Search" style="width: 240px" @keyup.enter="search" @clear="search" />
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="search">
          <el-option label="正常" :value="1" />
          <el-option label="已禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
      <span class="customer-total">共 {{ total }} 位客户</span>
    </div>

    <el-table v-loading="loading" :data="records" stripe empty-text="暂无客户数据">
      <el-table-column label="客户" min-width="200">
        <template #default="{ row }">
          <div class="cell-primary">{{ row.nickname }}</div>
          <div class="cell-secondary">{{ row.companyName || '未填写公司' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="手机号" width="140">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column label="信用额度" width="130">
        <template #default="{ row }"><strong>{{ formatCurrency(row.creditLimit) }}</strong></template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="light">{{ row.status === 1 ? '正常' : '已禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="170">
        <template #default="{ row }"><span class="cell-secondary">{{ row.createdAt?.replace('T', ' ').slice(0, 16) || '-' }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" @click="editCredit(row)">调额度</el-button>
          <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="customer-pagination">
      <el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="prev, pager, next" @current-change="load" />
    </div>

    <el-dialog v-model="detailVisible" title="客户详情" width="560px">
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <div class="detail-head">
            <el-avatar :size="52" :src="detail.user.avatarUrl || undefined">{{ (detail.user.nickname || '客').slice(0, 1) }}</el-avatar>
            <div>
              <strong>{{ detail.user.nickname }}</strong>
              <p>{{ detail.user.companyName || '未填写公司' }} · {{ detail.user.phone || '未绑定手机' }}</p>
            </div>
            <el-tag :type="detail.user.status === 1 ? 'success' : 'danger'" effect="light">{{ detail.user.status === 1 ? '正常' : '已禁用' }}</el-tag>
          </div>
          <div class="detail-stats">
            <div class="detail-stat"><span>订单总数</span><strong>{{ detail.orderCount }}</strong></div>
            <div class="detail-stat"><span>累计消费</span><strong>{{ formatCurrency(detail.totalSpent) }}</strong></div>
            <div class="detail-stat"><span>信用额度</span><strong>{{ formatCurrency(detail.user.creditLimit) }}</strong></div>
            <div class="detail-stat"><span>最近下单</span><strong>{{ detail.lastOrderAt ? detail.lastOrderAt.replace('T', ' ').slice(0, 16) : '暂无' }}</strong></div>
          </div>
          <div class="detail-meta">
            <p><span>用户 ID</span>{{ detail.user.id }}</p>
            <p><span>注册时间</span>{{ detail.user.createdAt?.replace('T', ' ').slice(0, 19) || '-' }}</p>
            <p><span>OpenID</span>{{ detail.user.openid || '-' }}</p>
          </div>
        </template>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.customer-panel{background:#fff;border:1px solid #e3ebf0;border-radius:12px;padding:20px 22px}
.customer-toolbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}
.customer-filters{display:flex;gap:10px;align-items:center}
.customer-total{font-size:12px;color:#81919d}
.cell-primary{font-size:13px;font-weight:600;color:#294556}
.cell-secondary{font-size:11px;color:#91a2ad}
.customer-pagination{display:flex;justify-content:flex-end;margin-top:16px}
.detail-body{min-height:180px}
.detail-head{display:flex;align-items:center;gap:14px;padding-bottom:16px;border-bottom:1px solid #eef3f6}
.detail-head>div{flex:1;display:flex;flex-direction:column;gap:4px}
.detail-head strong{font-size:16px;color:#152b3d}
.detail-head p{margin:0;font-size:12px;color:#81919d}
.detail-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:18px 0}
.detail-stat{background:#f6f9fb;border-radius:10px;padding:12px;display:flex;flex-direction:column;gap:6px}
.detail-stat span{font-size:11px;color:#81919d}
.detail-stat strong{font-size:14px;color:#152b3d}
.detail-meta p{display:flex;justify-content:space-between;margin:8px 0;font-size:12px;color:#496273}
.detail-meta span{color:#91a2ad}
</style>
