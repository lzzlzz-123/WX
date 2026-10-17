import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layout/AppLayout.vue'
import LoginView from '../views/LoginView.vue'
import OverviewView from '../views/OverviewView.vue'
import ProductManagement from '../components/ProductManagement.vue'
import OrderManagement from '../components/OrderManagement.vue'
import InquiryManagement from '../components/InquiryManagement.vue'
import CustomerManagement from '../components/CustomerManagement.vue'
import AnalyticsManagement from '../components/AnalyticsManagement.vue'
import SettingManagement from '../components/SettingManagement.vue'
import AdminManagement from '../views/AdminManagement.vue'
import InvoiceManagement from '../views/InvoiceManagement.vue'

const routes = [
  { path: '/login', name: 'login', component: LoginView, meta: { title: '登录' } },
  {
    path: '/',
    component: AppLayout,
    redirect: '/overview',
    children: [
      { path: 'overview', name: 'overview', component: OverviewView, meta: { title: '经营概览' } },
      { path: 'orders', name: 'orders', component: OrderManagement, meta: { title: '订单管理' } },
      { path: 'inquiries', name: 'inquiries', component: InquiryManagement, meta: { title: '询价报价' } },
      { path: 'invoices', name: 'invoices', component: InvoiceManagement, meta: { title: '开票管理' } },
      { path: 'products', name: 'products', component: ProductManagement, meta: { title: '商品管理' } },
      { path: 'customers', name: 'customers', component: CustomerManagement, meta: { title: '客户管理' } },
      { path: 'analytics', name: 'analytics', component: AnalyticsManagement, meta: { title: '数据统计' } },
      { path: 'settings', name: 'settings', component: SettingManagement, meta: { title: '系统设置' } },
      { path: 'admins', name: 'admins', component: AdminManagement, meta: { title: '账号权限' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/overview' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('buildmart_token')
  if (to.path !== '/login' && !token) {
    return { path: '/login' }
  }
  if (to.path === '/login' && token) {
    return { path: '/overview' }
  }
  document.title = to.meta?.title ? `${to.meta.title} - 筑云清松运营后台` : '筑云清松运营后台'
  return true
})

export default router
