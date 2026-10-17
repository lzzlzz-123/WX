const api = require('../../common/wx-api')

Page({
  data: { mode: 'inquiry', product: '', quantity: '', unit: '吨', deliveryAddress: '', remark: '', receiverName: '', receiverPhone: '', receiverAddress: '', address: null, items: [], total: '0.00' },
  onLoad(options) {
    // 询价/下单需要登录：未登录先去登录页。
    if (!api.currentUser()) {
      wx.navigateTo({ url: '/pages/login/index' })
      return
    }
    const mode = options?.mode === 'checkout' ? 'checkout' : 'inquiry'
    this.setData({ mode })
    if (mode === 'checkout') this.loadCheckout()
  },
  onProduct(event) { this.setData({ product: event.detail.value }) },
  onQuantity(event) { this.setData({ quantity: event.detail.value }) },
  onAddress(event) { this.setData({ deliveryAddress: event.detail.value }) },
  onRemark(event) { this.setData({ remark: event.detail.value }) },
  onReceiverName(event) { this.setData({ receiverName: event.detail.value }) },
  onReceiverPhone(event) { this.setData({ receiverPhone: event.detail.value }) },
  onReceiverAddress(event) { this.setData({ receiverAddress: event.detail.value }) },
  async loadCheckout() {
    try {
      const [cart, addresses] = await Promise.all([api.cart(), api.addresses()])
      const selected = (cart || []).filter(item => item.selected === 1)
      const items = await Promise.all(selected.map(async item => ({ ...item, product: await api.product(item.productId) })))
      const address = (addresses || []).find(item => item.isDefault === 1) || addresses?.[0] || null
      const total = items.reduce((sum, item) => sum + Number(item.product?.basePrice || 0) * Number(item.quantity), 0)
      this.setData({ items, address, total: total.toFixed(2) })
    } catch (error) {
      wx.showToast({ title: error.message || '订单信息加载失败', icon: 'none' })
    }
  },
  async submitInquiry() {
    if (!this.data.product || !Number(this.data.quantity)) {
      wx.showToast({ title: '请填写商品和采购数量', icon: 'none' })
      return
    }
    try {
      await api.createInquiry({ productName: this.data.product, quantity: Number(this.data.quantity), unit: this.data.unit, deliveryAddress: this.data.deliveryAddress, remark: this.data.remark })
      wx.showToast({ title: '询价单已提交', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error.message || '提交失败', icon: 'none' })
    }
  },
  async submitOrder() {
    if (!this.data.items.length) {
      wx.showToast({ title: '请确认采购车商品', icon: 'none' })
      return
    }
    try {
      let address = this.data.address
      if (!address) {
        if (!this.data.receiverName || !/^1\d{10}$/.test(this.data.receiverPhone) || !this.data.receiverAddress) {
          wx.showToast({ title: '请填写完整收货信息', icon: 'none' })
          return
        }
        address = await api.saveAddress({ receiverName: this.data.receiverName, receiverPhone: this.data.receiverPhone, detailAddress: this.data.receiverAddress, isDefault: 1 })
      }
      const addressText = [address.province, address.city, address.district, address.detailAddress].filter(Boolean).join(' ')
      const order = await api.createOrder({
        items: this.data.items.map(item => ({ productId: item.productId, quantity: item.quantity })),
        receiverName: address.receiverName,
        receiverPhone: address.receiverPhone,
        receiverAddress: addressText,
        deliveryMode: 'CITY_DELIVERY',
        remark: this.data.remark
      })
      await Promise.all((this.data.items || []).map(item => api.deleteCart(item.id).catch(() => null)))
      wx.showToast({ title: `订单 ${order.orderNo} 已创建`, icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error.message || '下单失败', icon: 'none' })
    }
  },
  submit() { return this.data.mode === 'checkout' ? this.submitOrder() : this.submitInquiry() }
})
