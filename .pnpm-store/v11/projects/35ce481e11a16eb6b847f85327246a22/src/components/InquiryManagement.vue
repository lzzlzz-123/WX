<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'

const records = ref([])
const total = ref(0)
const loading = ref(false)

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
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag type="warning">{{ row.status === 'PENDING_QUOTE' ? '待报价' : row.status }}</el-tag></template></el-table-column>
    </el-table>
  </section>
</template>
