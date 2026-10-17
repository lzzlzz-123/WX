<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'

const records = ref([])
const total = ref(0)
const loading = ref(false)
const status = ref('')
const statusMap = { PENDING: ['待开具', 'warning'], ISSUED: ['已开具', 'success'], REJECTED: ['已驳回', 'info'] }

async function load() {
  loading.value = true
  try {
    const response = await adminApi.invoiceRequests({ page: 1, size: 50, status: status.value || undefined })
    records.value = response.data.records
    total.value = response.data.total
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function issue(row) {
  await ElMessageBox.confirm(`确认为订单 ${row.orderId} 开具 ¥${row.amount} 的发票？`, '开具发票')
  try {
    await adminApi.issueInvoice(row.id)
    ElMessage.success('已标记开具')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回开票申请', {
    inputValidator: v => (v && v.trim()) || '请填写驳回原因'
  })
  try {
    await adminApi.rejectInvoice(row.id, { reason: value.trim() })
    ElMessage.success('已驳回')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="page-heading">
    <div><p class="eyebrow">OPERATIONS CENTER</p><h1>开票管理</h1><p class="heading-note">处理小程序用户的订单开票申请。</p></div>
  </section>
  <section class="module-panel">
    <div class="module-toolbar"><div class="module-search"><el-select v-model="status" placeholder="全部状态" clearable style="width:160px" @change="load"><el-option label="待开具" value="PENDING" /><el-option label="已开具" value="ISSUED" /><el-option label="已驳回" value="REJECTED" /></el-select><el-button :icon="Refresh" @click="load">刷新</el-button></div><span class="module-total">共 {{ total }} 条申请</span></div>
    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="id" label="申请号" width="90" />
      <el-table-column prop="orderId" label="订单 ID" width="110" />
      <el-table-column label="金额" width="130"><template #default="{ row }"><strong>¥ {{ row.amount }}</strong></template></el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ row.remark || '-' }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="(statusMap[row.status] || [row.status,'info'])[1]">{{ (statusMap[row.status] || [row.status,'info'])[0] }}</el-tag></template></el-table-column>
      <el-table-column prop="rejectReason" label="驳回原因" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ row.rejectReason || '-' }}</template></el-table-column>
      <el-table-column prop="createdAt" label="申请时间" width="160"><template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 16) }}</template></el-table-column>
      <el-table-column label="操作" width="140" align="right"><template #default="{ row }"><template v-if="row.status === 'PENDING'"><el-button link type="primary" @click="issue(row)">开具</el-button><el-button link type="danger" @click="reject(row)">驳回</el-button></template><span v-else class="table-secondary">已处理</span></template></el-table-column>
    </el-table>
  </section>
</template>
