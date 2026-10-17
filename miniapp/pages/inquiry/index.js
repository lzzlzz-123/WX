const api = require('../../common/wx-api')

const UNITS = ['吨', '立方米', '袋', '件', '车']

const STATUS_TEXT = {
  PENDING_QUOTE: '待报价',
  QUOTED: '已报价',
  CLOSED: '已关闭'
}

Page({
  data: {
    tab: 'records', // records 询价记录 | create 提交询价
    units: UNITS,
    unitIndex: 0,
    form: { productName: '', specification: '', quantity: '', expectedPrice: '', deliveryAddress: '', remark: '' },
    submitting: false,
    records: [],
    page: 1,
    size: 10,
    total: 0,
    loading: false,
    statusText: STATUS_TEXT
  },
  onShow() {
    if (!api.currentUser()) {
      wx.navigateTo({ url: '/pages/login/index' })
      return
    }
    this.reload()
  },
  onReachBottom() {
    if (this.data.tab === 'records' && this.data.records.length < this.data.total) this.loadPage(this.data.page + 1)
  },
  switchTab(event) {
    this.setData({ tab: event.currentTarget.dataset.tab })
  },
  onInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: event.detail.value })
  },
  onUnit(event) {
    this.setData({ unitIndex: Number(event.detail.value) })
  },
  async submit() {
    if (this.data.submitting) return
    const form = this.data.form
    if (!form.productName.trim()) {
      wx.showToast({ title: '请填写商品名称', icon: 'none' })
      return
    }
    if (!Number(form.quantity) || Number(form.quantity) <= 0) {
      wx.showToast({ title: '请填写正确的采购数量', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      await api.createInquiry({
        productName: form.productName.trim(),
        specification: form.specification.trim() || null,
        quantity: Number(form.quantity),
        unit: this.data.units[this.data.unitIndex],
        expectedPrice: Number(form.expectedPrice) > 0 ? Number(form.expectedPrice) : null,
        deliveryAddress: form.deliveryAddress.trim() || null,
        remark: form.remark.trim() || null
      })
      wx.showToast({ title: '询价单已提交', icon: 'success' })
      this.setData({
        form: { productName: '', specification: '', quantity: '', expectedPrice: '', deliveryAddress: '', remark: '' },
        unitIndex: 0,
        tab: 'records'
      })
      this.reload()
    } catch (error) {
      wx.showToast({ title: error.message || '提交失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
  reload() {
    this.setData({ page: 1, records: [] })
    this.loadPage(1)
  },
  async loadPage(page) {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const result = await api.inquiries({ page, size: this.data.size })
      const records = (result?.records || []).map(view => {
        const inquiry = view.inquiry || {}
        const quote = view.quote
        return {
          id: inquiry.id,
          inquiryNo: inquiry.inquiryNo,
          productName: inquiry.productName,
          specification: inquiry.specification,
          quantityText: `${inquiry.quantity} ${inquiry.unit || ''}`.trim(),
          expectedPrice: inquiry.expectedPrice,
          status: inquiry.status,
          statusLabel: STATUS_TEXT[inquiry.status] || inquiry.status,
          createdText: (inquiry.createdAt || '').replace('T', ' ').slice(0, 16),
          quotePrice: quote?.quotePrice ?? null,
          quoteDeliveryFee: quote?.deliveryFee ?? null,
          quoteValidUntil: quote?.validUntil || '',
          quoteRemark: quote?.remark || ''
        }
      })
      this.setData({
        records: page === 1 ? records : [...this.data.records, ...records],
        total: result?.total || 0,
        page
      })
    } catch (error) {
      if (api.isNotLoggedIn(error)) {
        wx.navigateTo({ url: '/pages/login/index' })
        return
      }
      wx.showToast({ title: error.message || '询价记录加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  }
})
