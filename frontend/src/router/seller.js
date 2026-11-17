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
      { path: 'customers',   name: 'SellerCustomers',    component: () => import('@/views/seller/CustomerList.vue'),  meta: { title: '客户管理' } },

      // 👇 挑战需求 B：订单管理（B.1 订单升级）
      { path: 'orders',      name: 'SellerOrders',       component: () => import('@/views/seller/OrderList.vue'),     meta: { title: '订单管理' } },
      // 👇 挑战需求 B：历史管理独立页面（不再和商品管理放在一起）
      { path: 'history',     name: 'SellerHistory',      component: () => import('@/views/seller/HistoryList.vue'),   meta: { title: '历史管理' } }
    ]
  }
]

export default sellerRoutes