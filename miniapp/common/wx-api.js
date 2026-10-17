const DEFAULT_BASE_URL = 'http://localhost:8080/api/miniapp'
// 真机调试时电脑与手机需在同一局域网；改成你电脑的实际 IP（ipconfig 查看，换网络后要更新）。
const DEVICE_BASE_URL = 'http://192.168.3.24:8080/api/miniapp'
let userPromise = null

const { swr, cacheRemove, MiniappCache } = require('./wx-cache')

function isPhysicalMiniProgram() {
  try {
    const info = wx.getDeviceInfo ? wx.getDeviceInfo() : wx.getSystemInfoSync()
    return !!info && info.platform !== 'devtools'
  } catch (_) {
    return false
  }
}

function baseUrl() {
  const stored = wx.getStorageSync('apiBaseUrl')
  // 真机上忽略 localhost/127.0.0.1 的历史缓存地址（那是开发者工具用的），否则请求会发到手机自己。
  const storedInvalidOnDevice = stored && isPhysicalMiniProgram() && /^(https?:\/\/)?(localhost|127\.0\.0\.1)(:|\/|$)/.test(stored)
  const url = (stored && !storedInvalidOnDevice) ? stored : (isPhysicalMiniProgram() ? DEVICE_BASE_URL : DEFAULT_BASE_URL)
  return String(url).replace(/\/+$/, '')
}

function notLoggedInError() {
  const error = new Error('notLoggedIn')
  error.code = 'NOT_LOGGED_IN'
  return error
}

function isNotLoggedIn(error) {
  return error && (error.code === 'NOT_LOGGED_IN' || error.message === 'notLoggedIn')
}

function clearAuth() {
  cacheRemove(MiniappCache.key.cart(wx.getStorageSync('userId')))
  wx.removeStorageSync('userId')
  wx.removeStorageSync('token')
  wx.removeStorageSync('userProfile')
  wx.removeStorageSync('miniappAuthMode')
}

// 当前已登录用户的缓存资料；未登录返回 null（同步、不触发网络）。
function currentUser() {
  const profile = wx.getStorageSync('userProfile')
  return (wx.getStorageSync('token') && profile) ? profile : null
}

function saveAuth(user) {
  wx.setStorageSync('miniappAuthMode', 'wechat')
  wx.setStorageSync('userId', user.userId)
  wx.setStorageSync('token', user.token)
  wx.setStorageSync('userProfile', user)
}

function request(path, options = {}) {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token')
    wx.request({
      url: `${baseUrl()}${path}`,
      method: options.method || 'GET',
      data: options.data,
      timeout: options.timeout || 10000,
      header: {
        ...(options.header || {}),
        ...(options.skipAuth || !token ? {} : { Authorization: `Bearer ${token}` })
      },
      success(response) {
        const body = response.data
        if (response.statusCode === 401) {
          clearAuth()
          reject(notLoggedInError())
          return
        }
        if (response.statusCode >= 200 && response.statusCode < 300 && body?.code === 0) {
          resolve(body.data)
        } else {
          reject(new Error(body?.message || `请求失败（${response.statusCode || '网络'}）`))
        }
      },
      fail: reject
    })
  })
}

function getWechatCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: result => result?.code ? resolve(result.code) : reject(new Error('微信登录未返回 code')),
      fail: reject
    })
  })
}

// code 换 openid 的静默登录：仅用于已注册老用户 token 失效后的续期，
// 不作为新用户注册入口（新用户请走 loginWithPhone）。
async function login(payload = {}) {
  const code = payload.code || await getWechatCode()
  const data = { code }
  if (payload.nickname) data.nickname = payload.nickname
  if (payload.phone) data.phone = payload.phone
  const user = await request('/auth/login', { method: 'POST', data, skipAuth: true })
  saveAuth(user)
  return user
}

// 手机号一键登录/注册：wx.login code + getPhoneNumber code 一起换。
async function loginWithPhone(phoneCode) {
  const code = await getWechatCode()
  const user = await request('/auth/login-phone', {
    method: 'POST',
    data: { loginCode: code, phoneCode },
    skipAuth: true
  })
  saveAuth(user)
  return user
}

// 测试短信服务：发送验证码（不真实发短信，响应里 testCode 直接回显）。
function sendSmsCode(phone) {
  return request('/auth/sms-code', { method: 'POST', data: { phone }, skipAuth: true })
}

// 短信验证码登录/注册：手机号首次使用自动注册。
async function loginWithSms(phone, code, nickname) {
  const user = await request('/auth/login-sms', {
    method: 'POST',
    data: { phone, code, nickname },
    skipAuth: true
  })
  saveAuth(user)
  return user
}

// 已登录 → 返回缓存；老用户 token 失效 → 静默续期一次；从未登录 → 拒绝 notLoggedIn。
async function ensureUser() {
  const cached = currentUser()
  if (cached) return cached
  if (wx.getStorageSync('userId')) {
    if (!userPromise) {
      userPromise = login()
        .catch(error => { clearAuth(); throw error })
        .finally(() => { userPromise = null })
    }
    return userPromise
  }
  throw notLoggedInError()
}

async function apiRequest(path, options = {}) {
  if (!options.skipAuth && !currentUser()) await ensureUser()
  return request(path, options)
}

// 头像等文件的完整访问地址：/files/... 挂在 /api 下而非 /api/miniapp 下。
function fileUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//.test(path)) return path
  return baseUrl().replace(/\/miniapp$/, '') + path
}

async function refreshProfile() {
  const profile = await apiRequest('/auth/profile')
  const cached = wx.getStorageSync('userProfile') || {}
  const merged = { ...cached, ...profile }
  wx.setStorageSync('userProfile', merged)
  return merged
}

async function updateProfile(data) {
  const profile = await apiRequest('/auth/profile', { method: 'PUT', data })
  const cached = wx.getStorageSync('userProfile') || {}
  const merged = { ...cached, ...profile }
  wx.setStorageSync('userProfile', merged)
  return merged
}

// 上传 chooseAvatar 返回的临时头像文件。
function uploadAvatar(filePath) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${baseUrl()}/auth/avatar`,
      filePath,
      name: 'file',
      header: { Authorization: `Bearer ${wx.getStorageSync('token')}` },
      success(response) {
        if (response.statusCode === 401) {
          clearAuth()
          reject(notLoggedInError())
          return
        }
        let body = null
        try { body = JSON.parse(response.data) } catch (_) { /* ignore */ }
        if (response.statusCode >= 200 && response.statusCode < 300 && body?.code === 0) {
          const cached = wx.getStorageSync('userProfile') || {}
          const merged = { ...cached, avatarUrl: body.data.avatarUrl }
          wx.setStorageSync('userProfile', merged)
          resolve(body.data)
        } else {
          reject(new Error(body?.message || `上传失败（${response.statusCode}）`))
        }
      },
      fail: reject
    })
  })
}

const api = {
  apiRequest,
  ensureUser,
  login,
  loginWithPhone,
  sendSmsCode,
  loginWithSms,
  currentUser,
  clearAuth,
  isNotLoggedIn,
  fileUrl,
  refreshProfile,
  updateProfile,
  uploadAvatar,
  // 商品目录无需登录，游客也能逛。
  categories: () => apiRequest('/catalog/categories', { skipAuth: true }),
  products: params => apiRequest(`/catalog/products?page=${params?.page || 1}&size=${params?.size || 20}${params?.keyword ? `&keyword=${encodeURIComponent(params.keyword)}` : ''}`, { skipAuth: true }),
  product: id => apiRequest(`/catalog/products/${id}`, { skipAuth: true }),
  cart: () => apiRequest('/cart'),
  addCart: (productId, quantity = 1) => apiRequest('/cart', { method: 'POST', data: { productId, quantity } }),
  updateCart: (id, data) => apiRequest(`/cart/${id}`, { method: 'PUT', data }),
  deleteCart: id => apiRequest(`/cart/${id}`, { method: 'DELETE' }),
  addresses: () => apiRequest('/addresses'),
  saveAddress: data => apiRequest('/addresses', { method: 'POST', data }),
  deleteAddress: id => apiRequest(`/addresses/${id}`, { method: 'DELETE' }),
  createOrder: data => apiRequest('/orders', { method: 'POST', data }),
  orders: params => apiRequest(`/orders?page=${params?.page || 1}&size=${params?.size || 20}${params?.status ? `&status=${encodeURIComponent(params.status)}` : ''}`),
  order: id => apiRequest(`/orders/${id}`),
  cancelOrder: id => apiRequest(`/orders/${id}/cancel`, { method: 'POST' }),
  createInquiry: data => apiRequest('/inquiries', { method: 'POST', data }),
  inquiries: params => apiRequest(`/inquiries?page=${params?.page || 1}&size=${params?.size || 20}${params?.status ? `&status=${encodeURIComponent(params.status)}` : ''}`),
  invoices: () => apiRequest('/invoices'),
  saveInvoice: data => apiRequest('/invoices', { method: 'POST', data }),
  deleteInvoice: id => apiRequest(`/invoices/${id}`, { method: 'DELETE' }),
  setDefaultInvoice: id => apiRequest(`/invoices/${id}/default`, { method: 'POST' })
}

// ---- 带本地缓存的读接口（商品目录 / 详情 / 购物车快照） ----
// 写操作后必须调用 invalidateCartCache，保证下一次读取回源。

function invalidateCartCache() {
  cacheRemove(MiniappCache.key.cart(wx.getStorageSync('userId')))
}

// 缓存版读接口：onRefresh 在后台刷新拿到新数据时回调（SWR）。
const cachedApi = {
  categories: onRefresh => swr(
    MiniappCache.key.categories(),
    () => api.categories(),
    MiniappCache.TTL.categories,
    onRefresh
  ),
  products: (params, onRefresh) => swr(
    MiniappCache.key.productList(params?.page || 1, params?.size || 20, params?.keyword),
    () => api.products(params),
    MiniappCache.TTL.productList,
    onRefresh
  ),
  product: (id, onRefresh) => swr(
    MiniappCache.key.productDetail(id),
    () => api.product(id),
    MiniappCache.TTL.productDetail,
    onRefresh
  ),
  cart: onRefresh => swr(
    MiniappCache.key.cart(wx.getStorageSync('userId')),
    () => api.cart(),
    MiniappCache.TTL.cart,
    onRefresh
  )
}

// 加购/改数量/删除：先写后端，成功后清本地购物车缓存。
async function addCartCached(productId, quantity = 1) {
  const result = await api.addCart(productId, quantity)
  invalidateCartCache()
  return result
}

async function updateCartCached(id, data) {
  const result = await api.updateCart(id, data)
  invalidateCartCache()
  return result
}

async function deleteCartCached(id) {
  const result = await api.deleteCart(id)
  invalidateCartCache()
  return result
}

api.invalidateCartCache = invalidateCartCache
api.cachedApi = cachedApi
api.addCartCached = addCartCached
api.updateCartCached = updateCartCached
api.deleteCartCached = deleteCartCached

module.exports = api
