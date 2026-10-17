const api = require('../../common/wx-api')

const EMPTY_FORM = { id: null, receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: 0 }

Page({
  data: {
    addresses: [],
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
    this.loadAddresses()
  },
  async loadAddresses() {
    this.setData({ loading: true })
    try {
      const addresses = await api.addresses()
      this.setData({ addresses: addresses || [] })
    } catch (error) {
      if (api.isNotLoggedIn(error)) {
        wx.navigateTo({ url: '/pages/login/index' })
        return
      }
      wx.showToast({ title: error.message || '地址加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
  onInput(event) {
    this.setData({ [`form.${event.currentTarget.dataset.field}`]: event.detail.value })
  },
  onDefaultSwitch(event) {
    this.setData({ 'form.isDefault': event.detail.value ? 1 : 0 })
  },
  addAddress() {
    this.setData({ editing: true, form: { ...EMPTY_FORM } })
  },
  editAddress(event) {
    const item = event.currentTarget.dataset.item
    this.setData({
      editing: true,
      form: {
        id: item.id,
        receiverName: item.receiverName || '',
        receiverPhone: item.receiverPhone || '',
        province: item.province || '',
        city: item.city || '',
        district: item.district || '',
        detailAddress: item.detailAddress || '',
        isDefault: item.isDefault || 0
      }
    })
  },
  cancelEdit() {
    this.setData({ editing: false, form: { ...EMPTY_FORM } })
  },
  async saveAddress() {
    if (this.data.saving) return
    const form = this.data.form
    if (!form.receiverName.trim()) {
      wx.showToast({ title: '请填写收货人姓名', icon: 'none' })
      return
    }
    if (!/^1\d{10}$/.test(form.receiverPhone)) {
      wx.showToast({ title: '请填写正确的手机号', icon: 'none' })
      return
    }
    if (!form.detailAddress.trim()) {
      wx.showToast({ title: '请填写详细地址', icon: 'none' })
      return
    }
    this.setData({ saving: true })
    try {
      await api.saveAddress({
        id: form.id || undefined,
        receiverName: form.receiverName.trim(),
        receiverPhone: form.receiverPhone,
        province: form.province.trim() || null,
        city: form.city.trim() || null,
        district: form.district.trim() || null,
        detailAddress: form.detailAddress.trim(),
        isDefault: form.isDefault
      })
      wx.showToast({ title: '地址已保存', icon: 'success' })
      this.setData({ editing: false, form: { ...EMPTY_FORM } })
      this.loadAddresses()
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  },
  async deleteAddress(event) {
    const id = event.currentTarget.dataset.id
    const { confirm } = await wx.showModal({ title: '删除地址', content: '确定删除这条收货地址吗？' })
    if (!confirm) return
    try {
      await api.deleteAddress(id)
      wx.showToast({ title: '已删除', icon: 'success' })
      this.loadAddresses()
    } catch (error) {
      wx.showToast({ title: error.message || '删除失败', icon: 'none' })
    }
  },
  async setDefault(event) {
    const item = event.currentTarget.dataset.item
    if (item.isDefault === 1) return
    try {
      // 后端保存接口在 isDefault=1 时会自动清掉其它默认。
      await api.saveAddress({ id: item.id, isDefault: 1 })
      wx.showToast({ title: '已设为默认', icon: 'success' })
      this.loadAddresses()
    } catch (error) {
      wx.showToast({ title: error.message || '设置失败', icon: 'none' })
    }
  }
})
