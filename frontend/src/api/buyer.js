/* ============================================================================
 * 买家端接口层（迭代 3：收藏 / 购物车 / 订单）
 * ----------------------------------------------------------------------------
 * 路径与字段已按后端实际实现对齐（backend: CartController / FavoriteController /
 * OrderController），与《接口契约》文档不一致的地方在注释里标了「后端实际」。
 *
 * ⚠️ 本文件内所有 `if (MOCK_ENABLED)` 行都是前端自测用的 Mock 分支。
 *    联调/上线时：删除下面的 mock import 段 + 每行 `if (MOCK_ENABLED) return ...`
 *    （全局搜索 MOCK_ENABLED 即可定位）
 * ========================================================================== */
import request from '@/utils/request'

/* ===================== 🧪 MOCK 开关（联调时删除本段） ===================== */
import { MOCK_ENABLED, mockApi } from './__mock__/buyerMock'
/* ========================================================================= */

/* ============================================================================
 * 字段适配：后端 DTO → 页面沿用的字段名
 * 页面（Cart.vue / Favorites.vue）读的是 name / image / available，
 * 后端返回的是 productName / imageUrl，在这里统一转一次，页面不用改。
 * ========================================================================== */
function adaptCartItem(i) {
  return {
    cartId: i.cartId,
    productId: String(i.productId),
    name: i.productName,
    image: i.imageUrl,
    price: i.price,
    stock: i.stock,
    quantity: Number(i.quantity),
    subtotal: i.subtotal,
    available: i.available !== false,
    statusText: i.productStatusText
  }
}

function adaptCartList(res) {
  const items = (res && res.items) || []
  return {
    total: res?.itemCount ?? items.length,
    // 后端 selectedAmount = 已勾选项合计，口径与页面一致
    totalAmount: res?.selectedAmount ?? '0.00',
    list: items.map(adaptCartItem)
  }
}

function adaptFavoriteItem(i) {
  return {
    productId: String(i.productId),
    name: i.productName,
    image: i.imageUrl,
    price: i.price,
    available: i.available !== false,
    createdAt: i.createdAt
  }
}

function adaptFavoriteList(res) {
  return {
    total: res?.total ?? 0,
    list: ((res && res.list) || []).map(adaptFavoriteItem)
  }
}

// ================= 账号相关 =================
// 买家登录：后端返回 { token, buyer }
export function login(data) {
  if (MOCK_ENABLED) return mockApi.login(data)
  return request.post('/buyer/login', data)
}

// 买家注册
export function register(data) {
  if (MOCK_ENABLED) return mockApi.register(data)
  return request.post('/buyer/register', data)
}

// ================= 商品相关 =================
// 商品列表：{ page, pageSize, categoryId, keyword }（后端只返回在售且有库存的商品）
export function getProductList(params) {
  if (MOCK_ENABLED) return mockApi.getProductList(params)
  return request.get('/buyer/products', { params })
}

// 商品详情：含 images 图库与 description 富文本
export function getProductDetail(id) {
  if (MOCK_ENABLED) return mockApi.getProductDetail(id)
  return request.get(`/buyer/products/${id}`)
}

// 两级分类树
export function getCategories() {
  if (MOCK_ENABLED) return mockApi.getCategories()
  return request.get('/category/tree')
}

// ================= 收藏（B.2-1） =================
// 后端实际路径：/api/buyer/favorites（复数）
export function addFavorite(productId) {
  if (MOCK_ENABLED) return mockApi.addFavorite(productId)
  return request.post('/buyer/favorites', { productId: Number(productId) })
}

export function removeFavorite(productId) {
  if (MOCK_ENABLED) return mockApi.removeFavorite(productId)
  return request.delete(`/buyer/favorites/${encodeURIComponent(productId)}`)
}

// 收藏列表：页面传 { page, size }，后端参数名是 pageSize
export function getFavoriteList(params = {}) {
  if (MOCK_ENABLED) return mockApi.getFavoriteList(params)
  return request
    .get('/buyer/favorites', {
      params: {
        page: Number(params.page) || 1,
        pageSize: Number(params.size || params.pageSize) || 10
      }
    })
    .then(adaptFavoriteList)
}

// ================= 购物车（B.2-2） =================
// 后端实际路径：/api/buyer/cart
export function addToCart(productId, quantity = 1) {
  if (MOCK_ENABLED) return mockApi.addToCart(productId, quantity)
  return request.post('/buyer/cart', {
    productId: Number(productId),
    quantity: Number(quantity)
  })
}

export function getCartList() {
  if (MOCK_ENABLED) return mockApi.getCartList()
  return request.get('/buyer/cart').then(adaptCartList)
}

// ⚠️ 后端购物车以 productId 定位条目（不是 cartId），所以入参是 productId
export function updateCartQuantity(productId, quantity) {
  if (MOCK_ENABLED) return mockApi.updateCartQuantity(productId, quantity)
  return request.put(`/buyer/cart/${encodeURIComponent(productId)}`, {
    quantity: Number(quantity)
  })
}

// 同样以 productId 定位
export function removeCartItem(productId) {
  if (MOCK_ENABLED) return mockApi.removeCartItem(productId)
  return request.delete(`/buyer/cart/${encodeURIComponent(productId)}`)
}

/**
 * 结算下单（B.2-3）
 * ⚠️ 后端没有「一步结算」端点，实际是三段式：
 *    1) POST /buyer/cart/checkout-check   结算前库存校验 → allEnough=false 即 40924
 *    2) POST /order                       建单（只认 productId + quantity）
 *    3) DELETE /buyer/cart/{productId}    已下单条目出车
 * 入参是商品ID数组（后端全程以 productId 标识购物车条目）。
 */
export async function checkoutCart(productIds) {
  if (MOCK_ENABLED) return mockApi.checkoutCart(productIds)

  const ids = (productIds || []).map(String)
  if (ids.length === 0) {
    return Promise.reject({ code: 40925, message: '请先勾选要结算的商品' })
  }

  // 1) 库存校验
  const check = await request.post('/buyer/cart/checkout-check', {
    productIds: ids.map(Number)
  })
  if (check && check.allEnough === false) {
    const shortage = (check.items || [])
      .filter(i => i.enough === false)
      .map(i => ({
        productId: String(i.productId),
        name: i.productName,
        stock: i.stock,
        quantity: i.quantity
      }))
    return Promise.reject({
      code: 40924,
      message: '部分商品库存不足',
      data: { shortage }
    })
  }

  // 2) 取数量后建单
  const cart = await request.get('/buyer/cart')
  const qtyMap = new Map(
    ((cart && cart.items) || []).map(i => [String(i.productId), i.quantity])
  )
  const items = ids
    .filter(id => qtyMap.has(id))
    .map(id => ({ productId: id, quantity: qtyMap.get(id) }))

  if (items.length === 0) {
    return Promise.reject({ code: 40925, message: '购物车中没有可结算的商品' })
  }

  const order = await request.post('/order', { items })

  // 3) 出车（后端下单不会自动清购物车）
  await Promise.all(
    ids.map(id => request.delete(`/buyer/cart/${encodeURIComponent(id)}`).catch(() => {}))
  )

  return { orderNo: order?.orderNo, totalAmount: order?.totalAmount }
}

// 购物车多件转收藏（B.2-4）：后端路径 /buyer/cart/to-favorites，返回 { moved, skipped }
export async function cartToFavorite(productIds) {
  if (MOCK_ENABLED) return mockApi.cartToFavorite(productIds)
  const res = await request.post('/buyer/cart/to-favorites', {
    productIds: (productIds || []).map(Number)
  })
  return { converted: res?.moved ?? 0, skipped: res?.skipped ?? 0 }
}

// ================= 订单（B.1） =================
// 我的订单（含已取消，按下单时间倒序）：参数名是 page + size
export function getMyOrderList(params = {}) {
  if (MOCK_ENABLED) return mockApi.getMyOrderList(params)
  return request.get('/order/my', {
    params: {
      page: Number(params.page) || 1,
      size: Number(params.size) || 10
    }
  })
}

export function getOrderDetail(orderNo) {
  if (MOCK_ENABLED) return mockApi.getOrderDetail(orderNo)
  return request.get(`/order/${encodeURIComponent(orderNo)}`)
}

// 取消订单：客户可取消 10/20/30；40921 状态不符、40923 已终态
export function cancelOrder(orderNo) {
  if (MOCK_ENABLED) return mockApi.cancelOrder(orderNo)
  return request.post(`/order/${encodeURIComponent(orderNo)}/cancel`)
}

// ================= 已下线：购买意向（迭代 3 起由订单取代，仅保留兼容） =================
// 注意：该接口不做 mock，迭代 3 已无买家页面调用
export function submitIntent(data) {
  return request.post('/buyer/intent', data)
}
