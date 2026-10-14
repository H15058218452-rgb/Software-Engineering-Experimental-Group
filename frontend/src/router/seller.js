// src/router/seller.js
const sellerRoutes = [
  {
    path: '/seller/login',
    name: 'SellerLogin',
    component: () => import('@/views/seller/Login.vue'),
    meta: { title: '卖家登录' }
  },
  {
    path: '/seller',
    component: () => import('@/views/seller/Layout.vue'),
    redirect: '/seller/goods',
    meta: { requiresAuth: true }, // 与 index.js 守卫字段名一致,子路由自动继承
    children: [
      { path: 'goods',       name: 'SellerGoodsList',    component: () => import('@/views/seller/GoodsList.vue'),     meta: { title: '历史商品' } },
      { path: 'publish',     name: 'SellerPublish',      component: () => import('@/views/seller/PublishGoods.vue'),  meta: { title: '发布商品' } },
      { path: 'edit/:id',    name: 'SellerEdit',         component: () => import('@/views/seller/PublishGoods.vue'),  meta: { title: '编辑商品' } },
      { path: 'intents',     name: 'SellerIntents',      component: () => import('@/views/seller/IntentList.vue'),  meta: { title: '意向购买人' } },
      { path: 'password',    name: 'SellerPassword',     component: () => import('@/views/seller/ChangePassword.vue'),meta: { title: '修改密码' } }
    ]
  }
]

export default sellerRoutes   