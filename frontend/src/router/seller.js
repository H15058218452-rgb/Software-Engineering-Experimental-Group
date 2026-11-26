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
      // 👇 编辑商品：复用发布页，靠 route.params.id 切到编辑模式
      { path: 'publish/:id', name: 'SellerGoodsEdit',    component: () => import('@/views/seller/PublishGoods.vue'),  meta: { title: '编辑商品' } },
      // 👇 B：购买意向机制已下线，「意向购买人」入口移除（历史记录在历史管理里只读留档）
      { path: 'password',    name: 'SellerPassword',     component: () => import('@/views/seller/ChangePassword.vue'),meta: { title: '修改密码' } },
      
      // 👇 新增客户管理路由
      { path: 'customers',   name: 'SellerCustomers',    component: () => import('@/views/seller/CustomerList.vue'),  meta: { title: '客户管理' } },

      // 👇 挑战需求 B：订单（B.1）—— 按客户查看，从客户管理进入，无全局订单列表
      { path: 'orders',      name: 'SellerOrders',       component: () => import('@/views/seller/OrderList.vue'),     meta: { title: '客户订单' } },
      // 👇 挑战需求 C：售后管理（C.3）—— 全部售后单，按状态筛选，可同意退货 / 仅退款 / 拒绝
      { path: 'after-sales', name: 'SellerAfterSales',   component: () => import('@/views/seller/AfterSaleList.vue'), meta: { title: '售后管理' } },
      // 👇 挑战需求 B：历史管理独立页面（不再和商品管理放在一起）
      { path: 'history',     name: 'SellerHistory',      component: () => import('@/views/seller/HistoryList.vue'),   meta: { title: '历史管理' } }
    ]
  }
]

export default sellerRoutes