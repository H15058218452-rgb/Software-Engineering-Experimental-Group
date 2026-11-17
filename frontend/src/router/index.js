import { createRouter, createWebHistory } from 'vue-router'
import buyerRoutes from './buyer'
import sellerRoutes from './seller'
import { STORAGE_KEYS } from '@/constants/storageKeys'

const router = createRouter({
  history: createWebHistory(),
  routes: [...buyerRoutes, ...sellerRoutes]
})

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth) {
    const token = localStorage.getItem(STORAGE_KEYS.SELLER_TOKEN)
    if (!token) return next('/seller/login')
  }

  if (to.meta.requiresBuyerAuth) {
    const token = localStorage.getItem(STORAGE_KEYS.BUYER_TOKEN)
    // 带上 redirect，登录后可回到原本要访问的页面（如购物车 / 收藏）
    if (!token) return next({ path: '/buyer/login', query: { redirect: to.fullPath } })
  }
  next()
})

export default router