import http from './http'

export const adminApi = {
  login: payload => http.post('/admin/auth/login', payload),
  dashboardSummary: () => http.get('/admin/dashboard/summary'),
  // 数据统计与趋势
  statisticsOverview: () => http.get('/admin/statistics/overview'),
  statisticsTrend: params => http.get('/admin/statistics/trend', { params }),
  products: params => http.get('/admin/products', { params }),
  saveProduct: payload => http.post('/admin/products', payload),
  changeProductStatus: (id, value) => http.patch(`/admin/products/${id}/status`, null, { params: { value } }),
  orders: params => http.get('/admin/orders', { params }),
  shipOrder: (id, payload) => http.post(`/admin/orders/${id}/ship`, payload),
  refundOrder: (id, payload) => http.post(`/admin/orders/${id}/refund`, payload),
  inquiries: params => http.get('/admin/inquiries', { params }),
  inquiryQuote: (id, data) => http.post(`/admin/inquiries/${id}/quote`, data),
  // 客户管理
  customers: params => http.get('/admin/customers', { params }),
  customer: id => http.get(`/admin/customers/${id}`),
  changeCustomerStatus: (id, value) => http.patch(`/admin/customers/${id}/status`, null, { params: { value } }),
  changeCustomerCredit: (id, value) => http.patch(`/admin/customers/${id}/credit`, null, { params: { value } }),
  // 系统设置
  settings: () => http.get('/admin/settings'),
  saveSettings: payload => http.put('/admin/settings', payload)
}
