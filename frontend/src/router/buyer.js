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
    path: '/buyer/cart',
    name: 'buyer-cart',
    component: () => import('@/views/buyer/Cart.vue'),
    meta: { requiresBuyerAuth: true } // 购物车需登录（B.2-2）
  },
  {
    path: '/buyer/checkout',
    name: 'buyer-checkout',
    component: () => import('@/views/buyer/Checkout.vue'),
    meta: { requiresBuyerAuth: true } // 下单页：选收货地址 + 确认商品（C.2-02）
  },
  {
    path: '/buyer/pay/:orderNo',
    name: 'buyer-pay',
    component: () => import('@/views/buyer/Payment.vue'),
    meta: { requiresBuyerAuth: true } // 支付页：选渠道 + 倒计时 + 轮询支付结果（C.1）
  },
  {
    path: '/buyer/favorites',
    name: 'buyer-favorites',
    component: () => import('@/views/buyer/Favorites.vue'),
    meta: { requiresBuyerAuth: true } // 收藏需登录（B.2-1）
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
  },
  {
    path: '/buyer/orders/:orderNo',
    name: 'buyer-order-detail',
    component: () => import('@/views/buyer/OrderDetail.vue'),
    meta: { requiresBuyerAuth: true } // 订单详情：地址快照 / 物流 / 确认收货 / 售后入口
  },
  {
    path: '/buyer/address',
    name: 'buyer-address',
    component: () => import('@/views/buyer/AddressList.vue'),
    meta: { requiresBuyerAuth: true } // 收货地址管理（C.2-01）
  },
  {
    path: '/buyer/after-sale/apply',
    name: 'buyer-after-sale-apply',
    component: () => import('@/views/buyer/AfterSaleApply.vue'),
    meta: { requiresBuyerAuth: true } // 发起售后申请（C.3-01）
  },
  {
    path: '/buyer/after-sales',
    name: 'buyer-after-sales',
    component: () => import('@/views/buyer/MyAfterSales.vue'),
    meta: { requiresBuyerAuth: true } // 我的售后单列表（C.3-02）
  },
  {
    path: '/buyer/after-sales/:afterSaleNo',
    name: 'buyer-after-sale-detail',
    component: () => import('@/views/buyer/AfterSaleDetail.vue'),
    meta: { requiresBuyerAuth: true } // 售后单详情 + 撤销（C.3-02 / C.3-07）
  }
]
