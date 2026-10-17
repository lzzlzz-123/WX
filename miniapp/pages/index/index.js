const api = require('../../common/wx-api')

Page({
  data: {
    categories: ['水泥建材', '钢材', '砂石料', '装饰辅材'],
    products: [
      { name: 'P.O 42.5 普通硅酸盐水泥', desc: '海螺 · 50kg/袋', price: '318', unit: '元/吨' },
      { name: 'HRB400E 螺纹钢', desc: '12mm · 国标', price: '3680', unit: '元/吨' },
      { name: '中粗砂（河砂）', desc: '含泥量 ≤ 3%', price: '120', unit: '元/吨' }
    ]
  },
  onLoad() {
    Promise.all([api.categories(), api.products({ page: 1, size: 6 })]).then(([categories, products]) => {
      this.setData({
        categories: (categories || []).map(item => item.name),
        products: (products?.records || []).map(item => ({
          id: item.id,
          name: item.name,
          desc: [item.brand, item.specification].filter(Boolean).join(' · '),
          price: item.basePrice,
          unit: `元/${item.unit}`
        }))
      })
    }).catch(error => console.warn('catalog api unavailable', error))
  },
  goGoods() { wx.switchTab({ url: '/pages/goods/list' }) },
  goInquiry() { wx.navigateTo({ url: '/pages/order/index?mode=inquiry' }) }
})
