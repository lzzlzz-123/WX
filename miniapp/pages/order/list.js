const api = require('../../common/wx-api')

const TABS = [
  { label: '全部', status: '' },
  { label: '待付款', status: 'PENDING_PAYMENT' },
  { label: '待发货', status: 'PENDING_SHIPMENT' },
  { label: '待收货', status: 'PENDING_RECEIPT' },
  { label: '已完成', status: 'COMPLETED' },
  { label: '已取消', status: 'CANCELLED' }
]

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

Page({
  data: {
    tabs: TABS,
    active: 0,
    orders: [],
    page: 1,
    size: 10,
    total: 0,
    loading: false,
    statusText: STATUS_TEXT
  },
  onLoad(options) {
    const index = TABS.findIndex(tab => tab.status === (options?.status || ''))
    this.setData({ active: index >= 0 ? index : 0 })
  },
  onShow() {
    if (!api.currentUser()) {
      wx.navigateTo({ url: '/pages/login/index' })
      return
    }
    this.reload()
  },
  onReachBottom() {
    if (this.data.orders.length < this.data.total) this.loadMore()
  },
  switchTab(event) {
    this.setData({ active: event.currentTarget.dataset.index }, () => this.reload())
  },
  reload() {
    this.setData({ page: 1, orders: [] })
    this.loadPage(1)
  },
  loadMore() {
    this.loadPage(this.data.page + 1)
  },
  async loadPage(page) {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const status = this.data.tabs[this.data.active].status
      const result = await api.orders({ page, size: this.data.size, status: status || undefined })
      const records = (result?.records || []).map(item => ({
        ...item,
        statusLabel: STATUS_TEXT[item.status] || item.status,
        createdText: (item.createdAt || '').replace('T', ' ').slice(0, 16)
      }))
      this.setData({
        orders: page === 1 ? records : [...this.data.orders, ...records],
        total: result?.total || 0,
        page
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
  openDetail(event) {
    wx.navigateTo({ url: `/pages/order/detail?id=${event.currentTarget.dataset.id}` })
  },
  async cancelOrder(event) {
    const id = event.currentTarget.dataset.id
    const { confirm } = await wx.showModal({ title: '取消订单', content: '确定取消这笔订单吗？取消后库存将释放。' })
    if (!confirm) return
    try {
      await api.cancelOrder(id)
      wx.showToast({ title: '订单已取消', icon: 'success' })
      this.reload()
    } catch (error) {
      wx.showToast({ title: error.message || '取消失败', icon: 'none' })
    }
  },
  async pay(event) {
    const id = event.currentTarget.dataset.id
    try {
      wx.showLoading({ title: '发起支付...', mask: true })
      const params = await api.payOrder(id)
      wx.hideLoading()
      await wx.requestPayment({
        timeStamp: params.timeStamp,
        nonceStr: params.nonceStr,
        package: params.package,
        signType: params.signType || 'RSA',
        paySign: params.paySign
      })
      wx.showToast({ title: '支付成功', icon: 'success' })
      this.reload()
    } catch (error) {
      wx.hideLoading()
      if (error && error.errMsg && error.errMsg.includes('cancel')) return
      wx.showToast({ title: error.message || '发起支付失败', icon: 'none' })
    }
  }
})
