<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'

const records = ref([])
const total = ref(0)
const loading = ref(false)
const status = ref('')
const statusMap = { PENDING_PAYMENT: ['待付款', 'info'], PENDING_SHIPMENT: ['待发货', 'warning'], PENDING_RECEIPT: ['待收货', 'primary'], COMPLETED: ['已完成', 'success'], CANCELLED: ['已取消', 'info'], REFUNDED: ['已退款', 'danger'], REFUNDING: ['退款中', 'danger'] }
const deliveryMap = { CITY_DELIVERY: '同城配送', LOGISTICS: '物流发运', PICKUP: '自提', SELF_PICKUP: '自提' }

const shipDialogVisible = ref(false)
const shipForm = ref({ id: null, orderNo: '', selfPickup: false, logisticsCompany: '', trackingNo: '', remark: '' })
const shipLoading = ref(false)

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

function openShipDialog(row) {
  const selfPickup = row.deliveryMode === 'SELF_PICKUP' || row.deliveryMode === 'PICKUP'
  shipForm.value = { id: row.id, orderNo: row.orderNo, selfPickup, logisticsCompany: '', trackingNo: '', remark: '' }
  shipDialogVisible.value = true
}

async function submitShip() {
  if (!shipForm.value.selfPickup && (!shipForm.value.logisticsCompany || !shipForm.value.trackingNo)) {
    ElMessage.warning('配送订单请填写物流公司和运单号')
    return
  }
  shipLoading.value = true
  try {
    const response = await adminApi.shipOrder(shipForm.value.id, {
      logisticsCompany: shipForm.value.logisticsCompany || undefined,
      trackingNo: shipForm.value.trackingNo || undefined,
      remark: shipForm.value.remark || undefined
    })
    shipDialogVisible.value = false
    const pickupCode = response?.data?.pickupCode
    ElMessage.success(pickupCode ? `已发货，自提核销码：${pickupCode}` : '订单已发货')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    shipLoading.value = false
  }
}

async function refund(row) {
  const { value } = await ElMessageBox.prompt(
    `订单 ${row.orderNo} 实付 ¥${row.payableAmount}，请输入退款原因（默认全额原路退回）`,
    '订单退款',
    { confirmButtonText: '确认退款', cancelButtonText: '取消', inputPlaceholder: '如：客户取消订单' }
  )
  try {
    await adminApi.refundOrder(row.id, { reason: value || undefined })
    ElMessage.success('退款申请已提交，结果以微信退款通知为准')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="module-panel">
    <div class="module-toolbar"><div class="module-search"><el-select v-model="status" placeholder="全部状态" clearable style="width:160px" @change="load"><el-option label="待付款" value="PENDING_PAYMENT" /><el-option label="待发货" value="PENDING_SHIPMENT" /><el-option label="待收货" value="PENDING_RECEIPT" /><el-option label="已完成" value="COMPLETED" /><el-option label="已取消" value="CANCELLED" /><el-option label="已退款" value="REFUNDED" /></el-select><el-button :icon="Refresh" @click="load">刷新</el-button></div><span class="module-total">共 {{ total }} 笔订单</span></div>
    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="orderNo" label="订单号" min-width="190" />
      <el-table-column label="收货人" width="150"><template #default="{ row }"><div class="table-primary">{{ row.receiverName || '-' }}</div><div class="table-secondary">{{ row.receiverPhone || '-' }}</div></template></el-table-column>
      <el-table-column prop="receiverAddress" label="收货地址" min-width="230" show-overflow-tooltip />
      <el-table-column label="金额" width="130"><template #default="{ row }"><strong>¥ {{ row.payableAmount }}</strong></template></el-table-column>
      <el-table-column label="配送" width="120"><template #default="{ row }">{{ deliveryMap[row.deliveryMode] || row.deliveryMode || '-' }}</template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="(statusMap[row.status] || [row.status,'info'])[1]">{{ (statusMap[row.status] || [row.status,'info'])[0] }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="140" align="right"><template #default="{ row }"><template v-if="row.status === 'PENDING_SHIPMENT'"><el-button link type="primary" @click="openShipDialog(row)">发货</el-button><el-button link type="danger" @click="refund(row)">退款</el-button></template><span v-else class="table-secondary">暂无操作</span></template></el-table-column>
    </el-table>

    <el-dialog v-model="shipDialogVisible" :title="`订单发货 ${shipForm.orderNo}`" width="440px">
      <el-form label-width="90px">
        <template v-if="shipForm.selfPickup">
          <el-alert type="info" :closable="false" title="自提订单：确认发货后系统自动生成核销码，客户凭码提货。" />
        </template>
        <template v-else>
          <el-form-item label="物流公司" required><el-input v-model="shipForm.logisticsCompany" placeholder="如：顺丰 / 货拉拉 / 自有车队" /></el-form-item>
          <el-form-item label="运单号" required><el-input v-model="shipForm.trackingNo" placeholder="请输入运单号" /></el-form-item>
        </template>
        <el-form-item label="发货备注"><el-input v-model="shipForm.remark" type="textarea" :rows="2" placeholder="选填" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="shipLoading" @click="submitShip">确认发货</el-button>
      </template>
    </el-dialog>
  </section>
</template>
