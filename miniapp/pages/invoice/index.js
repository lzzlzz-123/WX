const api = require('../../common/wx-api')

const EMPTY_FORM = {
  id: null, titleType: 'COMPANY', title: '', taxNo: '',
  bankName: '', bankAccount: '', registerAddress: '', registerPhone: '', email: '', isDefault: 0
}

Page({
  data: {
    invoices: [],
    loading: false,
    editing: false,
    saving: false,
    form: { ...EMPTY_FORM }
  },
  onShow() {
    if (!api.currentUser()) {
      wx.navigateTo({ url: '/pages/login/index' })
      return
    }
    this.loadInvoices()
  },
  async loadInvoices() {
    this.setData({ loading: true })
    try {
      const invoices = await api.invoices()
      this.setData({ invoices: invoices || [] })
    } catch (error) {
      if (api.isNotLoggedIn(error)) {
        wx.navigateTo({ url: '/pages/login/index' })
        return
      }
      wx.showToast({ title: error.message || '发票抬头加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
  onInput(event) {
    this.setData({ [`form.${event.currentTarget.dataset.field}`]: event.detail.value })
  },
  onTypeChange(event) {
    this.setData({ 'form.titleType': event.currentTarget.dataset.type })
  },
  onDefaultSwitch(event) {
    this.setData({ 'form.isDefault': event.detail.value ? 1 : 0 })
  },
  addInvoice() {
    this.setData({ editing: true, form: { ...EMPTY_FORM } })
  },
  editInvoice(event) {
    const item = event.currentTarget.dataset.item
    this.setData({
      editing: true,
      form: {
        id: item.id,
        titleType: item.titleType || 'COMPANY',
        title: item.title || '',
        taxNo: item.taxNo || '',
        bankName: item.bankName || '',
        bankAccount: item.bankAccount || '',
        registerAddress: item.registerAddress || '',
        registerPhone: item.registerPhone || '',
        email: item.email || '',
        isDefault: item.isDefault || 0
      }
    })
  },
  cancelEdit() {
    this.setData({ editing: false, form: { ...EMPTY_FORM } })
  },
  async saveInvoice() {
    if (this.data.saving) return
    const form = this.data.form
    if (!form.title.trim()) {
      wx.showToast({ title: form.titleType === 'COMPANY' ? '请填写企业名称' : '请填写个人姓名', icon: 'none' })
      return
    }
    if (form.titleType === 'COMPANY' && !form.taxNo.trim()) {
      wx.showToast({ title: '企业抬头必须填写税号', icon: 'none' })
      return
    }
    if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      wx.showToast({ title: '邮箱格式不正确', icon: 'none' })
      return
    }
    this.setData({ saving: true })
    try {
      await api.saveInvoice({
        id: form.id || undefined,
        titleType: form.titleType,
        title: form.title.trim(),
        taxNo: form.taxNo.trim() || null,
        bankName: form.bankName.trim() || null,
        bankAccount: form.bankAccount.trim() || null,
        registerAddress: form.registerAddress.trim() || null,
        registerPhone: form.registerPhone.trim() || null,
        email: form.email.trim() || null,
        isDefault: form.isDefault
      })
      wx.showToast({ title: '发票抬头已保存', icon: 'success' })
      this.setData({ editing: false, form: { ...EMPTY_FORM } })
      this.loadInvoices()
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  },
  async deleteInvoice(event) {
    const id = event.currentTarget.dataset.id
    const { confirm } = await wx.showModal({ title: '删除抬头', content: '确定删除这条发票抬头吗？' })
    if (!confirm) return
    try {
      await api.deleteInvoice(id)
      wx.showToast({ title: '已删除', icon: 'success' })
      this.loadInvoices()
    } catch (error) {
      wx.showToast({ title: error.message || '删除失败', icon: 'none' })
    }
  },
  async setDefault(event) {
    const item = event.currentTarget.dataset.item
    if (item.isDefault === 1) return
    try {
      await api.setDefaultInvoice(item.id)
      wx.showToast({ title: '已设为默认', icon: 'success' })
      this.loadInvoices()
    } catch (error) {
      wx.showToast({ title: error.message || '设置失败', icon: 'none' })
    }
  }
})
