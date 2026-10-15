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
  next()
})

export default router