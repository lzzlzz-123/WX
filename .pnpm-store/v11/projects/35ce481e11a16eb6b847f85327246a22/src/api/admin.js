import http from './http'

export const adminApi = {
  login: payload => http.post('/admin/auth/login', payload),
  dashboardSummary: () => http.get('/admin/dashboard/summary'),
  products: params => http.get('/admin/products', { params }),
  saveProduct: payload => http.post('/admin/products', payload),
  changeProductStatus: (id, value) => http.patch(`/admin/products/${id}/status`, null, { params: { value } }),
  orders: params => http.get('/admin/orders', { params }),
  shipOrder: id => http.post(`/admin/orders/${id}/ship`),
  inquiries: params => http.get('/admin/inquiries', { params })
}
