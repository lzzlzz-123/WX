// 小程序端本地缓存工具（微信原生版）：基于 wx.setStorageSync 的 TTL 缓存 + SWR（先旧后新）策略。
// 只缓存可重建的读数据（商品目录、详情、购物车快照），后端接口仍是唯一数据源。

const PREFIX = 'cache:'

function storageKey(key) {
  return PREFIX + key
}

// 写入缓存，ttl 单位毫秒。
function cacheSet(key, data, ttl) {
  try {
    wx.setStorageSync(storageKey(key), { data, expireAt: Date.now() + ttl })
  } catch (_) { /* 存储满等异常直接忽略，不影响业务 */ }
}

// 读取未过期缓存；过期或不存在返回 null。
function cacheGet(key) {
  try {
    const entry = wx.getStorageSync(storageKey(key))
    if (!entry || typeof entry !== 'object') return null
    if (!entry.expireAt || entry.expireAt < Date.now()) {
      wx.removeStorageSync(storageKey(key))
      return null
    }
    return entry.data
  } catch (_) {
    return null
  }
}

// 读取缓存（含过期数据），用于 SWR 先展示旧数据；不存在返回 null。
function cacheGetStale(key) {
  try {
    const entry = wx.getStorageSync(storageKey(key))
    return entry && typeof entry === 'object' ? entry.data : null
  } catch (_) {
    return null
  }
}

function cacheRemove(key) {
  try {
    wx.removeStorageSync(storageKey(key))
  } catch (_) { /* ignore */ }
}

/**
 * SWR：有旧缓存立即返回旧数据，同时后台刷新并调用 onRefresh；
 * 无缓存则等待网络结果。网络失败但有旧数据时静默使用旧数据。
 *
 * @param key      缓存 key
 * @param fetcher  返回 Promise 的数据获取函数
 * @param ttl      缓存毫秒数
 * @param onRefresh 可选，后台刷新成功后的回调（用于更新页面）
 */
async function swr(key, fetcher, ttl, onRefresh) {
  const fresh = cacheGet(key)
  if (fresh !== null) return fresh

  const stale = cacheGetStale(key)
  if (stale !== null) {
    // 后台刷新，不阻塞当前返回。
    fetcher()
      .then(data => {
        cacheSet(key, data, ttl)
        if (typeof onRefresh === 'function') onRefresh(data)
      })
      .catch(() => { /* 保持旧数据 */ })
    return stale
  }

  const data = await fetcher()
  cacheSet(key, data, ttl)
  return data
}

// 统一缓存 key 与 TTL，便于排查与失效管理。
const MiniappCache = {
  TTL: {
    categories: 10 * 60 * 1000, // 分类变化少：10 分钟
    productList: 60 * 1000,     // 商品列表：1 分钟（与后端列表缓存一致）
    productDetail: 5 * 60 * 1000, // 商品详情：5 分钟
    cart: 30 * 1000             // 购物车快照：30 秒，变更后立即失效
  },
  key: {
    categories: () => 'catalog:categories',
    productList: (page, size, keyword) => `catalog:products:${page}:${size}:${(keyword || '_').toLowerCase()}`,
    productDetail: id => `catalog:product:${id}`,
    cart: userId => `cart:${userId || 'anon'}`
  }
}

module.exports = { cacheSet, cacheGet, cacheGetStale, cacheRemove, swr, MiniappCache }
