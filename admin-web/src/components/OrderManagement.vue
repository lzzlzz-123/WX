<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'

const records = ref([])
const total = ref(0)
const loading = ref(false)
const status = ref('')
const statusMap = { PENDING_PAYMENT: ['待付款', 'info'], PENDING_SHIPMENT: ['待发货', 'warning'], PENDING_RECEIPT: ['待收货', 'primary'], COMPLETED: ['已完成', 'success'], CANCELLED: ['已取消', 'info'], REFUNDING: ['退款中', 'danger'] }

async function load() {
  loading.value = true
  try {
    const response = await adminApi.orders({ page: 1, size: 50, status: status.value || undefined })
    records.value = response.data.records
    total.value = response.data.total
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function ship(row) {
  await ElMessageBox.confirm(`确认订单 ${row.orderNo} 已安排发货？`, '订单发货')
  try {
    await adminApi.shipOrder(row.id)
    ElMessage.success('订单已发货')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="module-panel">
    <div class="module-toolbar"><div class="module-search"><el-select v-model="status" placeholder="全部状态" clearable style="width:160px" @change="load"><el-option label="待付款" value="PENDING_PAYMENT" /><el-option label="待发货" value="PENDING_SHIPMENT" /><el-option label="待收货" value="PENDING_RECEIPT" /><el-option label="已完成" value="COMPLETED" /></el-select><el-button :icon="Refresh" @click="load">刷新</el-button></div><span class="module-total">共 {{ total }} 笔订单</span></div>
    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="orderNo" label="订单号" min-width="190" />
      <el-table-column label="收货人" width="150"><template #default="{ row }"><div class="table-primary">{{ row.receiverName || '-' }}</div><div class="table-secondary">{{ row.receiverPhone || '-' }}</div></template></el-table-column>
      <el-table-column prop="receiverAddress" label="收货地址" min-width="230" show-overflow-tooltip />
      <el-table-column label="金额" width="130"><template #default="{ row }"><strong>¥ {{ row.payableAmount }}</strong></template></el-table-column>
      <el-table-column label="配送" width="120"><template #default="{ row }">{{ row.deliveryMode || '-' }}</template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="(statusMap[row.status] || [row.status,'info'])[1]">{{ (statusMap[row.status] || [row.status,'info'])[0] }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="100" align="right"><template #default="{ row }"><el-button v-if="row.status === 'PENDING_SHIPMENT'" link type="primary" @click="ship(row)">发货</el-button><span v-else class="table-secondary">暂无操作</span></template></el-table-column>
    </el-table>
  </section>
</template>
