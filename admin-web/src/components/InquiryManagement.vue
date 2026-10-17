<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'

const records = ref([])
const total = ref(0)
const loading = ref(false)

const quoteVisible = ref(false)
const quoteSaving = ref(false)
const quoteTarget = ref(null)
const quoteForm = reactive({ quotePrice: null, deliveryFee: 0, validUntil: '', remark: '' })

const STATUS_TEXT = { PENDING_QUOTE: '待报价', QUOTED: '已报价', CLOSED: '已关闭' }
const STATUS_TYPE = { PENDING_QUOTE: 'warning', QUOTED: 'success', CLOSED: 'info' }

async function load() {
  loading.value = true
  try {
    const response = await adminApi.inquiries({ page: 1, size: 50 })
    records.value = response.data.records
    total.value = response.data.total
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function openQuote(row) {
  quoteTarget.value = row
  quoteForm.quotePrice = row.expectedPrice || null
  quoteForm.deliveryFee = 0
  quoteForm.validUntil = ''
  quoteForm.remark = ''
  quoteVisible.value = true
}

async function submitQuote() {
  if (!quoteForm.quotePrice || Number(quoteForm.quotePrice) <= 0) {
    ElMessage.warning('请填写正确的报价')
    return
  }
  quoteSaving.value = true
  try {
    await adminApi.inquiryQuote(quoteTarget.value.id, {
      quotePrice: Number(quoteForm.quotePrice),
      deliveryFee: Number(quoteForm.deliveryFee) || 0,
      validUntil: quoteForm.validUntil || null,
      remark: quoteForm.remark || null
    })
    ElMessage.success('报价已发送，用户可在小程序查看')
    quoteVisible.value = false
    load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    quoteSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="module-panel">
    <div class="module-toolbar"><span class="module-total">共 {{ total }} 条询价</span><el-button :icon="Refresh" @click="load">刷新</el-button></div>
    <el-table v-loading="loading" :data="records" empty-text="暂无询价记录" stripe>
      <el-table-column prop="inquiryNo" label="询价编号" min-width="180" />
      <el-table-column prop="productName" label="商品" min-width="200" />
      <el-table-column prop="specification" label="规格" min-width="150" />
      <el-table-column label="数量" width="120"><template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template></el-table-column>
      <el-table-column label="期望价格" width="130"><template #default="{ row }">{{ row.expectedPrice ? `¥ ${row.expectedPrice}` : '待协商' }}</template></el-table-column>
      <el-table-column prop="deliveryAddress" label="交货地址" min-width="230" show-overflow-tooltip />
      <el-table-column prop="deadline" label="截止日期" width="120" />
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="STATUS_TYPE[row.status] || 'info'">{{ STATUS_TEXT[row.status] || row.status }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING_QUOTE'" type="primary" size="small" @click="openQuote(row)">报价</el-button>
          <el-button v-else-if="row.status === 'QUOTED'" size="small" @click="openQuote(row)">重新报价</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="quoteVisible" :title="`报价 - ${quoteTarget?.productName || ''}`" width="480px">
      <el-form label-width="90px">
        <el-form-item label="询价数量"><span>{{ quoteTarget?.quantity }} {{ quoteTarget?.unit }}</span></el-form-item>
        <el-form-item label="报价单价" required>
          <el-input-number v-model="quoteForm.quotePrice" :min="0" :precision="2" :controls="false" placeholder="元" style="width: 200px" />
          <span style="margin-left: 8px; color: #909399">元 / {{ quoteTarget?.unit || '单位' }}</span>
        </el-form-item>
        <el-form-item label="配送费">
          <el-input-number v-model="quoteForm.deliveryFee" :min="0" :precision="2" :controls="false" style="width: 200px" />
          <span style="margin-left: 8px; color: #909399">元</span>
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker v-model="quoteForm.validUntil" type="date" value-format="YYYY-MM-DD" placeholder="选填" style="width: 200px" />
        </el-form-item>
        <el-form-item label="报价备注">
          <el-input v-model="quoteForm.remark" type="textarea" :rows="2" placeholder="选填，如含装卸、账期说明等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quoteVisible = false">取消</el-button>
        <el-button type="primary" :loading="quoteSaving" @click="submitQuote">发送报价</el-button>
      </template>
    </el-dialog>
  </section>
</template>
