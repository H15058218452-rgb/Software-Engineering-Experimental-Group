export default [
  {
    path: '/seller/login',
    name: 'seller-login',
    component: () => import('@/views/seller/Login.vue')
  },
  {
    path: '/seller/publish',
    name: 'seller-publish',
    component: () => import('@/views/seller/PublishGoods.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/seller/list',
    name: 'seller-list',
    component: () => import('@/views/seller/GoodsList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/seller/intent',
    name: 'seller-intent',
    component: () => import('@/views/seller/IntentDetail.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/seller/password',
    name: 'seller-password',
    component: () => import('@/views/seller/ChangePassword.vue'),
    meta: { requiresAuth: true }
  }
]