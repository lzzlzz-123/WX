App({
  globalData: {
    apiBaseUrl: 'http://localhost:8080/api/miniapp'
  },
  onLaunch() {
    // 不再启动时静默登录：游客可浏览商品，进入采购车/订单/我的时再引导登录。
    // 注意：不要在这里把 localhost 写进 apiBaseUrl 缓存，真机会把请求发到手机自己；
    // 地址选择逻辑统一在 common/wx-api.js 的 baseUrl()。
  }
})
