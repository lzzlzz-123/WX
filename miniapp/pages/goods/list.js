const api = require('../../common/wx-api')

Page({
  data: {
    keyword: '',
    categories: ['全部', '水泥建材', '钢材', '砂石料', '装饰辅材'],
    categoryIds: [null, 1, 2, 3, 4],
    active: 0,
    allGoods: [],
    goods: [
      { name: 'P.O 42.5 普通硅酸盐水泥', spec: '海螺 · 50kg/袋', price: '318', unit: '元/吨', stock: '库存充足' },
      { name: 'HRB400E 螺纹钢', spec: '12mm · 国标', price: '3680', unit: '元/吨', stock: '现货 86 吨' },
      { name: '中粗砂（河砂）', spec: '含泥量 ≤ 3%', price: '120', unit: '元/吨', stock: '现货 420 吨' }
    ]
  },
  onLoad() { this.loadProducts() },
  applyCatalog(categories, result) {
    const allGoods = (result?.records || []).map(item => ({
        id: item.id,
        categoryId: item.categoryId,
        name: item.name,
        spec: [item.brand, item.specification].filter(Boolean).join(' · '),
        price: item.basePrice,
        unit: item.unit,
        stock: Number(item.stock) > 0 ? `库存 ${item.stock} ${item.unit}` : '库存不足'
      }))
    this.setData({
      categories: ['全部', ...(categories || []).map(item => item.name)],
      categoryIds: [null, ...(categories || []).map(item => item.id)],
      allGoods
    })
    this.refreshGoods()
  },
  async loadProducts() {
    // 缓存优先（SWR）：命中缓存秒开，后台刷新成功后自动更新列表。
    try {
      const [categories, result] = await Promise.all([
        api.cachedApi.categories(fresh => this.applyCatalog(fresh, this._lastProducts)),
        api.cachedApi.products({ page: 1, size: 50 }, fresh => { this._lastProducts = fresh; this.applyCatalog(this._lastCategories, fresh) })
      ])
      this._lastCategories = categories
      this._lastProducts = result
      this.applyCatalog(categories, result)
    } catch (error) {
      console.warn('catalog api unavailable', error)
    }
  },
  refreshGoods() {
    const keyword = this.data.keyword.trim()
    const categoryId = this.data.categoryIds[this.data.active]
    const goods = this.data.allGoods.filter(item =>
      (!keyword || item.name.includes(keyword) || item.spec.includes(keyword)) && (!categoryId || item.categoryId === categoryId)
    )
    this.setData({ goods })
  },
  onSearch(event) { this.setData({ keyword: event.detail.value }, () => this.refreshGoods()) },
  selectCategory(event) { this.setData({ active: event.currentTarget.dataset.index }, () => this.refreshGoods()) },
  async add(event) {
    try {
      await api.addCartCached(Number(event.currentTarget.dataset.id), 1)
      wx.showToast({ title: event.currentTarget.dataset.name.slice(0, 8) + '已加购', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error.message || '加购失败', icon: 'none' })
    }
  }
})
