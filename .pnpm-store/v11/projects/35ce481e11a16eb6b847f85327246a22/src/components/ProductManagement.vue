<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const keyword = ref('')
const records = ref([])
const total = ref(0)
const page = ref(1)
const form = reactive({ id: null, categoryId: 1, sku: '', name: '', brand: '', grade: '', specification: '', unit: '吨', basePrice: 0, stock: 0, minOrderQty: 1, deliveryModes: 'CITY_DELIVERY', status: 1 })

async function load() {
  loading.value = true
  try {
    const response = await adminApi.products({ page: page.value, size: 20, keyword: keyword.value || undefined })
    records.value = response.data.records
    total.value = response.data.total
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, categoryId: 1, sku: '', name: '', brand: '', grade: '', specification: '', unit: '吨', basePrice: 0, stock: 0, minOrderQty: 1, deliveryModes: 'CITY_DELIVERY', status: 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.name || !form.sku || !form.unit || Number(form.basePrice) <= 0) {
    ElMessage.warning('请填写商品名称、SKU、单位和价格')
    return
  }
  saving.value = true
  try {
    await adminApi.saveProduct({ product: { ...form }, tiers: [] })
    ElMessage.success('商品已保存')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确认${next === 1 ? '上架' : '下架'}“${row.name}”？`, '商品状态')
  try {
    await adminApi.changeProductStatus(row.id, next)
    row.status = next
    ElMessage.success(next === 1 ? '已上架' : '已下架')
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="module-panel">
    <div class="module-toolbar">
      <div class="module-search"><el-input v-model="keyword" placeholder="商品名称或 SKU" clearable @keyup.enter="load"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-button :icon="Refresh" @click="load">刷新</el-button></div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建商品</el-button>
    </div>
    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="sku" label="SKU" width="160" />
      <el-table-column label="商品" min-width="230"><template #default="{ row }"><div class="table-primary">{{ row.name }}</div><div class="table-secondary">{{ row.brand || '-' }} · {{ row.specification || '-' }}</div></template></el-table-column>
      <el-table-column label="价格" width="140"><template #default="{ row }">¥ {{ row.basePrice }} / {{ row.unit }}</template></el-table-column>
      <el-table-column label="库存" width="130"><template #default="{ row }">{{ row.stock }} {{ row.unit }}</template></el-table-column>
      <el-table-column label="起订量" width="110"><template #default="{ row }">{{ row.minOrderQty }} {{ row.unit }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '在售' : '已下架' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="150" align="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">{{ row.status === 1 ? '下架' : '上架' }}</el-button></template></el-table-column>
    </el-table>
    <div class="module-pagination"><span>共 {{ total }} 个商品</span><el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="prev, pager, next" @current-change="load" /></div>
  </section>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑商品' : '新建商品'" width="680px">
    <el-form label-position="top" class="product-form"><el-form-item label="商品名称"><el-input v-model="form.name" /></el-form-item><el-form-item label="SKU"><el-input v-model="form.sku" :disabled="Boolean(form.id)" /></el-form-item><el-form-item label="品牌"><el-input v-model="form.brand" /></el-form-item><el-form-item label="规格"><el-input v-model="form.specification" /></el-form-item><el-form-item label="单位"><el-select v-model="form.unit" style="width:100%"><el-option v-for="unit in ['吨','袋','立方米','平方米','根','米']" :key="unit" :label="unit" :value="unit" /></el-select></el-form-item><el-form-item label="基础价"><el-input-number v-model="form.basePrice" :min="0" :precision="2" style="width:100%" /></el-form-item><el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" :precision="3" style="width:100%" /></el-form-item><el-form-item label="起订量"><el-input-number v-model="form.minOrderQty" :min="0.001" :precision="3" style="width:100%" /></el-form-item></el-form>
    <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>
