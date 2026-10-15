export default [
  { path: '/', redirect: '/buyer/goods' },
  {
    path: '/buyer/goods',
    name: 'buyer-goods-list',
    component: () => import('@/views/buyer/GoodsList.vue')
  },
  {
    path: '/buyer/goods/detail',
    name: 'buyer-goods-detail',
    component: () => import('@/views/buyer/GoodsDetail.vue')
  },
  {
    path: '/buyer/intent/query',
    name: 'buyer-intent-query',
    component: () => import('@/views/buyer/IntentQuery.vue')
  },
  {
    path: '/buyer/intent/detail',
    name: 'buyer-intent-detail',
    component: () => import('@/views/buyer/IntentDetail.vue')
  }
]