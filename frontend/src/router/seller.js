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
    meta: { requiresAuth: true },
    children: [
      { path: 'goods',       name: 'SellerGoodsList',    component: () => import('@/views/seller/GoodsList.vue'),     meta: { title: '商品管理' } },
      { path: 'publish',     name: 'SellerPublish',      component: () => import('@/views/seller/PublishGoods.vue'),  meta: { title: '发布商品' } },
      { path: 'intents',     name: 'SellerIntents',      component: () => import('@/views/seller/IntentList.vue'),    meta: { title: '意向购买人' } },
      { path: 'password',    name: 'SellerPassword',     component: () => import('@/views/seller/ChangePassword.vue'),meta: { title: '修改密码' } },
      
      // 👇 新增客户管理路由
      { path: 'customers',   name: 'SellerCustomers',    component: () => import('@/views/seller/CustomerList.vue'),  meta: { title: '客户管理' } }
    ]
  }
]

export default sellerRoutes