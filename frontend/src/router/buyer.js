export default [
  { path: '/', redirect: '/buyer/goods' },
  {
    path: '/buyer/goods',
    name: 'buyer-goods-list',
    component: () => import('@/views/buyer/GoodsList.vue')
  },
  {
    path: '/buyer/goods/detail/:id', // 注意这里改成了动态路由，多商品需要传ID
    name: 'buyer-goods-detail',
    component: () => import('@/views/buyer/GoodsDetail.vue')
  },
  {
    path: '/buyer/login',
    name: 'buyer-login',
    component: () => import('@/views/buyer/Login.vue')
  },
  {
    path: '/buyer/register',
    name: 'buyer-register',
    component: () => import('@/views/buyer/Register.vue')
  },
  {
    path: '/buyer/orders',
    name: 'buyer-orders',
    component: () => import('@/views/buyer/MyOrders.vue'),
    meta: { requiresBuyerAuth: true } // 需要登录
  }
]