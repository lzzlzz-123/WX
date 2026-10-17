import axios from 'axios'

const http = axios.create({
  // Production is served behind Nginx, which proxies the same-origin /api path.
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000
})

http.interceptors.request.use(config => {
  const token = localStorage.getItem('buildmart_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(response => response.data, error => {
  const status = error.response?.status
  if (status === 401) {
    localStorage.removeItem('buildmart_token')
    window.dispatchEvent(new CustomEvent('buildmart-auth-expired'))
  }
  const message = error.response?.data?.message
    || (status === 401 ? '登录已失效，请重新登录' : '服务暂时不可用')
  const normalizedError = new Error(message)
  normalizedError.status = status
  return Promise.reject(normalizedError)
})

export default http
