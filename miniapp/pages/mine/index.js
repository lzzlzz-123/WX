const api = require('../../common/wx-api')

Page({
  data: {
    profile: { nickname: '微信用户', phone: '' },
    avatarUrl: '',
    counts: { pendingPayment: 0, pendingShipment: 0, shipping: 0, completed: 0 },
    address: '暂无收货地址'
  },
  onShow() {
    // 未登录先去登录页（手机号一键登录/注册）。
    if (!api.currentUser()) {
      wx.navigateTo({ url: '/pages/login/index' })
      return
    }
    this.loadMine()
  },
  openProfile() {
    wx.navigateTo({ url: '/pages/profile/index' })
  },
  openOrders(event) {
    const status = event?.currentTarget?.dataset?.status || ''
    wx.navigateTo({ url: `/pages/order/list${status ? `?status=${status}` : ''}` })
  },
  openInquiries() {
    wx.navigateTo({ url: '/pages/inquiry/index' })
  },
  openAddresses() {
    wx.navigateTo({ url: '/pages/address/index' })
  },
  openInvoices() {
    wx.navigateTo({ url: '/pages/invoice/index' })
  },
  async loadMine() {
    try {
      // 先从服务器拉最新资料（头像昵称改动立即生效），失败回退本地缓存。
      const [profile, orderPage, addresses] = await Promise.all([
        api.refreshProfile().catch(() => api.ensureUser()),
        api.orders({ page: 1, size: 100 }),
        api.addresses()
      ])
      const orders = orderPage?.records || []
      this.setData({
        profile,
        avatarUrl: api.fileUrl(profile?.avatarUrl),
        counts: {
          pendingPayment: orders.filter(item => item.status === 'PENDING_PAYMENT').length,
          pendingShipment: orders.filter(item => ['PAID', 'PENDING_SHIPMENT'].includes(item.status)).length,
          shipping: orders.filter(item => ['SHIPPED', 'DELIVERING'].includes(item.status)).length,
          completed: orders.filter(item => item.status === 'COMPLETED').length
        },
        address: (() => {
          const item = (addresses || []).find(address => address.isDefault === 1) || addresses?.[0]
          return item ? [item.city, item.district, item.detailAddress].filter(Boolean).join(' · ') : '暂无收货地址'
        })()
      })
    } catch (error) {
      if (api.isNotLoggedIn(error)) {
        wx.navigateTo({ url: '/pages/login/index' })
        return
      }
      console.warn('mine api unavailable', error)
    }
  }
})
