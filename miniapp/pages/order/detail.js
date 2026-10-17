const api = require('../../common/wx-api')

const STATUS_TEXT = {
  PENDING_PAYMENT: '待付款',
  PENDING_SHIPMENT: '待发货',
  PENDING_RECEIPT: '待收货',
  PAID: '待发货',
  SHIPPED: '配送中',
  DELIVERING: '配送中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款'
}

const DELIVERY_TEXT = {
  CITY_DELIVERY: '同城配送',
  SELF_PICKUP: '自提',
  LOGISTICS: '物流发运'
}

function fmt(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

Page({
  data: {
    id: null,
    order: null,
    items: [],
    shipments: [],
    statusLabel: '',
    deliveryLabel: '',
    loading: true
  },
  onLoad(options) {
    this.setData({ id: Number(options?.id) })
  },
  onShow() {
    if (!api.currentUser()) {
      wx.navigateTo({ url: '/pages/login/index' })
      return
    }
    this.loadDetail()
  },
  async loadDetail() {
    this.setData({ loading: true })
    try {
      const detail = await api.order(this.data.id)
      const order = detail?.order
      this.setData({
        order: order ? {
          ...order,
          createdText: fmt(order.createdAt),
          paidText: fmt(order.paidAt),
          shippedText: fmt(order.shippedAt),
          completedText: fmt(order.completedAt)
        } : null,
        items: detail?.items || [],
        shipments: detail?.shipments || [],
        statusLabel: STATUS_TEXT[order?.status] || order?.status || '',
        deliveryLabel: DELIVERY_TEXT[order?.deliveryMode] || order?.deliveryMode || ''
      })
    } catch (error) {
      if (api.isNotLoggedIn(error)) {
        wx.navigateTo({ url: '/pages/login/index' })
        return
      }
      wx.showToast({ title: error.message || '订单加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
  async cancelOrder() {
    const { confirm } = await wx.showModal({ title: '取消订单', content: '确定取消这笔订单吗？取消后库存将释放。' })
    if (!confirm) return
    try {
      await api.cancelOrder(this.data.id)
      wx.showToast({ title: '订单已取消', icon: 'success' })
      this.loadDetail()
    } catch (error) {
      wx.showToast({ title: error.message || '取消失败', icon: 'none' })
    }
  },
  copyOrderNo() {
    wx.setClipboardData({ data: this.data.order?.orderNo || '' })
  },
  async pay() {
    try {
      wx.showLoading({ title: '发起支付...', mask: true })
      const params = await api.payOrder(this.data.id)
      wx.hideLoading()
      await wx.requestPayment({
        timeStamp: params.timeStamp,
        nonceStr: params.nonceStr,
        package: params.package,
        signType: params.signType || 'RSA',
        paySign: params.paySign
      })
      wx.showToast({ title: '支付成功', icon: 'success' })
      this.loadDetail()
    } catch (error) {
      wx.hideLoading()
      if (error && error.errMsg && error.errMsg.includes('cancel')) {
        wx.showToast({ title: '已取消支付', icon: 'none' })
        return
      }
      wx.showToast({ title: error.message || '发起支付失败', icon: 'none' })
    }
  },
  async confirmReceipt() {
    const { confirm } = await wx.showModal({ title: '确认收货', content: '确认已收到全部货物吗？' })
    if (!confirm) return
    try {
      await api.confirmOrder(this.data.id)
      wx.showToast({ title: '已确认收货', icon: 'success' })
      this.loadDetail()
    } catch (error) {
      wx.showToast({ title: error.message || '操作失败', icon: 'none' })
    }
  },
  callReceiver() {
    if (this.data.order?.receiverPhone) wx.makePhoneCall({ phoneNumber: this.data.order.receiverPhone })
  }
})
