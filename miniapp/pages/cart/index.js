const api = require('../../common/wx-api')

Page({
  data: { items: [], total: '0.00' },
  onShow() {
    // 采购车需要登录：未登录先去登录页。
    if (!api.currentUser()) {
      wx.navigateTo({ url: '/pages/login/index' })
      return
    }
    this.loadCart()
  },
  async loadCart() {
    try {
      // 缓存优先（SWR）：命中缓存秒开，后台刷新成功后自动重渲染。
      const cart = await api.cachedApi.cart(() => this.loadCart())
      const items = await Promise.all((cart || []).map(async item => {
        const product = await api.cachedApi.product(item.productId)
        return {
          ...item,
          name: product?.name || '商品',
          spec: [product?.brand, product?.specification].filter(Boolean).join(' · '),
          price: product?.basePrice || 0,
          qty: item.quantity,
          unit: product?.unit || ''
        }
      }))
      const total = items.filter(item => item.selected === 1).reduce((sum, item) => sum + Number(item.price) * Number(item.qty), 0)
      this.setData({ items, total: total.toFixed(2) })
    } catch (error) {
      if (api.isNotLoggedIn(error)) {
        wx.navigateTo({ url: '/pages/login/index' })
        return
      }
      wx.showToast({ title: error.message || '购物车加载失败', icon: 'none' })
    }
  },
  async changeQuantity(event) {
    const item = event.currentTarget.dataset.item
    const quantity = Math.max(1, Number(item.qty) + Number(event.currentTarget.dataset.delta))
    try {
      await api.updateCartCached(item.id, { quantity })
      this.loadCart()
    } catch (error) {
      wx.showToast({ title: error.message || '更新失败', icon: 'none' })
    }
  },
  checkout() {
    if (Number(this.data.total) > 0) wx.navigateTo({ url: '/pages/order/index?mode=checkout' })
  }
})
