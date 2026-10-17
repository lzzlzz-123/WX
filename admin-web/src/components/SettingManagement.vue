<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'

// 已知配置项的展示元数据；后端返回的其他 key 也会以通用输入框展示。
const KNOWN_FIELDS = [
  { key: 'shop.name', label: '商城名称', type: 'text', hint: '展示在小程序与管理端的商城名称' },
  { key: 'shop.contact_phone', label: '客服电话', type: 'text', hint: '客户侧展示的联系电话' },
  { key: 'order.auto_cancel_minutes', label: '订单自动取消（分钟）', type: 'number', hint: '待支付订单超过该时长自动取消' },
  { key: 'order.default_freight', label: '默认运费（元）', type: 'number', hint: '下单时的默认运费金额' },
  { key: 'inquiry.quote_valid_days', label: '报价有效天数', type: 'number', hint: '询价单报价的默认有效期' },
  { key: 'stock.low_warning_enabled', label: '低库存预警', type: 'switch', hint: '开启后在经营概览展示库存预警' }
]

const loading = ref(false)
const saving = ref(false)
const form = ref({})
const extraKeys = ref([])

async function load() {
  loading.value = true
  try {
    const response = await adminApi.settings()
    const settings = response.data || {}
    const next = {}
    KNOWN_FIELDS.forEach(field => {
      next[field.key] = settings[field.key] ?? ''
    })
    form.value = next
    extraKeys.value = Object.keys(settings).filter(key => !KNOWN_FIELDS.some(field => field.key === key))
    // 额外 key 的初值也放入 form，便于编辑保存。
    extraKeys.value.forEach(key => { form.value[key] = settings[key] })
  } catch (error) {
    ElMessage.error(`设置加载失败：${error.message}`)
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const payload = {}
    KNOWN_FIELDS.forEach(field => {
      const value = form.value[field.key]
      payload[field.key] = value === null || value === undefined ? '' : String(value)
    })
    extraKeys.value.forEach(key => {
      payload[key] = form.value[key] === null || form.value[key] === undefined ? '' : String(form.value[key])
    })
    await adminApi.saveSettings(payload)
    ElMessage.success('系统设置已保存')
    await load()
  } catch (error) {
    ElMessage.error(`保存失败：${error.message}`)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="setting-panel" v-loading="loading">
    <div class="setting-head">
      <div>
        <h2>基础配置</h2>
        <p>保存后立即生效；配置值以字符串存储，数值与开关由使用方解析。</p>
      </div>
      <div>
        <el-button :icon="Refresh" @click="load">重置</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
      </div>
    </div>

    <el-form label-position="top" class="setting-form">
      <el-form-item v-for="field in KNOWN_FIELDS" :key="field.key">
        <template #label>
          <span class="field-label">{{ field.label }}<small>{{ field.key }}</small></span>
        </template>
        <el-input v-if="field.type === 'text'" v-model="form[field.key]" />
        <el-input-number v-else-if="field.type === 'number'" v-model="form[field.key]" :min="0" controls-position="right" style="width: 220px" />
        <div v-else-if="field.type === 'switch'" class="switch-row">
          <el-switch
            :model-value="form[field.key] === 'true'"
            @update:model-value="value => form[field.key] = value ? 'true' : 'false'"
          />
          <span class="switch-state">{{ form[field.key] === 'true' ? '已开启' : '已关闭' }}</span>
        </div>
        <p class="field-hint">{{ field.hint }}</p>
      </el-form-item>
    </el-form>

    <template v-if="extraKeys.length">
      <h3 class="extra-title">其他配置项</h3>
      <el-form label-position="top" class="setting-form">
        <el-form-item v-for="key in extraKeys" :key="key">
          <template #label><span class="field-label">{{ key }}</span></template>
          <el-input v-model="form[key]" />
        </el-form-item>
      </el-form>
    </template>
  </section>
</template>

<style scoped>
.setting-panel{background:#fff;border:1px solid #e3ebf0;border-radius:12px;padding:20px 22px;max-width:860px}
.setting-head{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:18px;padding-bottom:16px;border-bottom:1px solid #eef3f6}
.setting-head h2{margin:0;font-size:15px;color:#152b3d}
.setting-head p{margin:6px 0 0;font-size:12px;color:#81919d}
.setting-form{display:grid;grid-template-columns:1fr 1fr;column-gap:28px}
.field-label{display:flex;flex-direction:column;line-height:1.3;font-size:12px;font-weight:600;color:#496273}
.field-label small{font-weight:400;color:#9aa8b1;font-size:10px}
.field-hint{margin:4px 0 0;font-size:11px;color:#9aa8b1}
.switch-row{display:flex;align-items:center;gap:10px;height:32px}
.switch-state{font-size:12px;color:#81919d}
.extra-title{margin:10px 0 16px;font-size:13px;color:#152b3d;padding-top:14px;border-top:1px solid #eef3f6}
</style>
