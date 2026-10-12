import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/buyer/goods' },
  // 买家路由（同学写）
  { path: '/buyer/goods', component: () => import('@/views/buyer/GoodsDetail.vue') },
  { path: '/buyer/intent', component: () => import('@/views/buyer/SubmitIntent.vue') },
  // 卖家路由（你写）
  { path: '/seller/login', component: () => import('@/views/seller/Login.vue') },
  { path: '/seller/publish', component: () => import('@/views/seller/PublishGoods.vue') },
  { path: '/seller/list', component: () => import('@/views/seller/GoodsList.vue') },
  { path: '/seller/intent', component: () => import('@/views/seller/IntentDetail.vue') },
  { path: '/seller/password', component: () => import('@/views/seller/ChangePassword.vue') },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})