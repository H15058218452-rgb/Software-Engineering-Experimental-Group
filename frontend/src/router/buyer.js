export default [
  { path: '/', redirect: '/buyer/goods' },
  {
    path: '/buyer/goods',
    name: 'buyer-goods',
    component: () => import('@/views/buyer/GoodsDetail.vue')
  },
  {
    path: '/buyer/intent/submit',
    name: 'buyer-intent-submit',
    component: () => import('@/views/buyer/SubmitIntent.vue')
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