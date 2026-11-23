/* ============================================================================
 * 买家端接口层（迭代 4：支付 / 收货地址 / 物流 / 售后）
 * ----------------------------------------------------------------------------
 * 全部接口直连后端，不含任何 Mock 分支（正式版本）。
 * 路径与字段已按后端实际实现对齐（backend: OrderController / AddressController /
 * AfterSaleController / CartController / FavoriteController），
 * 与《接口契约》文档不一致的地方在注释里标了「后端实际」。
 * ========================================================================== */
import request from '@/utils/request'

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
  return request.post('/buyer/login', data)
}

// 买家注册
export function register(data) {
  return request.post('/buyer/register', data)
}

// ================= 商品相关 =================
// 商品列表：{ page, pageSize, categoryId, keyword }（后端只返回在售且有库存的商品）
export function getProductList(params) {
  return request.get('/buyer/products', { params })
}

// 商品详情：含 images 图库与 description 富文本
export function getProductDetail(id) {
  return request.get(`/buyer/products/${id}`)
}

// 两级分类树
export function getCategories() {
  return request.get('/category/tree')
}

// ================= 收藏（B.2-1） =================
// 后端实际路径：/api/buyer/favorites（复数）
export function addFavorite(productId) {
  return request.post('/buyer/favorites', { productId: Number(productId) })
}

export function removeFavorite(productId) {
  return request.delete(`/buyer/favorites/${encodeURIComponent(productId)}`)
}

// 收藏列表：页面传 { page, size }，后端参数名是 pageSize
export function getFavoriteList(params = {}) {
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
  return request.post('/buyer/cart', {
    productId: Number(productId),
    quantity: Number(quantity)
  })
}

export function getCartList() {
  return request.get('/buyer/cart').then(adaptCartList)
}

// ⚠️ 后端购物车以 productId 定位条目（不是 cartId），所以入参是 productId
export function updateCartQuantity(productId, quantity) {
  return request.put(`/buyer/cart/${encodeURIComponent(productId)}`, {
    quantity: Number(quantity)
  })
}

// 同样以 productId 定位
export function removeCartItem(productId) {
  return request.delete(`/buyer/cart/${encodeURIComponent(productId)}`)
}

/**
 * 下单（B.2-3 + 迭代 4 地址快照）
 * 建单接口迭代 4 起必须带 addressId：POST /order { addressId, items }
 *   40954 → 客户无收货地址，不能下单（前端先引导新增）
 *   40953 → 地址不属于当前客户
 *   40924 → 库存不足，data.shortage 带缺货清单
 * 返回 { orderNo, status: 15, statusText: '待支付', totalAmount, expireAt }
 */
export async function createOrder({ addressId, items } = {}) {
  if (!addressId) {
    return Promise.reject({ code: 40925, message: '请选择收货地址' })
  }
  return request.post('/order', {
    addressId: String(addressId),
    items: (items || []).map(i => ({
      productId: String(i.productId),
      quantity: Number(i.quantity)
    }))
  })
}

/**
 * 结算下单（B.2-3）
 * ⚠️ 后端没有「一步结算」端点，实际是三段式：
 *    1) POST /buyer/cart/checkout-check   结算前库存校验 → allEnough=false 即 40924
 *    2) POST /order                       建单（addressId + 商品明细）
 *    3) DELETE /buyer/cart/{productId}    已下单条目出车
 * 入参是商品ID数组（后端全程以 productId 标识购物车条目）+ 收货地址ID。
 */
export async function checkoutCart(productIds, addressId) {
  const ids = (productIds || []).map(String)
  if (ids.length === 0) {
    return Promise.reject({ code: 40925, message: '请先勾选要结算的商品' })
  }
  if (!addressId) {
    return Promise.reject({ code: 40925, message: '请选择收货地址' })
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

  const order = await request.post('/order', { addressId: String(addressId), items })

  // 3) 出车（后端下单不会自动清购物车）
  await Promise.all(
    ids.map(id => request.delete(`/buyer/cart/${encodeURIComponent(id)}`).catch(() => {}))
  )

  return {
    orderNo: order?.orderNo,
    status: order?.status,
    statusText: order?.statusText,
    totalAmount: order?.totalAmount,
    expireAt: order?.expireAt
  }
}

// 购物车多件转收藏（B.2-4）：后端路径 /buyer/cart/to-favorites，返回 { moved, skipped }
export async function cartToFavorite(productIds) {
  const res = await request.post('/buyer/cart/to-favorites', {
    productIds: (productIds || []).map(Number)
  })
  return { converted: res?.moved ?? 0, skipped: res?.skipped ?? 0 }
}

// ================= 订单（B.1） =================
// 我的订单（含已取消，按下单时间倒序）：参数名是 page + size
export function getMyOrderList(params = {}) {
  return request.get('/order/my', {
    params: {
      page: Number(params.page) || 1,
      size: Number(params.size) || 10
    }
  })
}

export function getOrderDetail(orderNo) {
  return request.get(`/order/${encodeURIComponent(orderNo)}`)
}

// 取消订单（C.1-04）：客户可取消 15 / 18 / 20 / 30；40921 状态不符、40923 已终态
// 已支付（18 及之后）取消会按原支付渠道退款，库存回补
export function cancelOrder(orderNo, reason = '') {
  return request.post(`/order/${encodeURIComponent(orderNo)}/cancel`, { reason })
}

// 确认收货（C.1-07）：仅「开始发货」40 可调，成功进入「交易完成」50 终态
export function receiveOrder(orderNo) {
  return request.post(`/order/${encodeURIComponent(orderNo)}/receive`)
}

// ================= 支付（C.1 · 挑战需求 C） =================
/* 支付渠道枚举：一次只能选一个，不支持组合支付（40942 渠道不支持） */
export const PAY_CHANNELS = [
  { value: 'BANK_CARD', label: '银行卡' },
  { value: 'CREDIT_CARD', label: '信用卡' },
  { value: 'ALIPAY', label: '支付宝' },
  { value: 'WECHAT', label: '微信' }
]

/**
 * 发起支付 POST /api/order/{orderNo}/pay
 * 返回 { paymentNo, orderNo, channel, amount, payUrl, expireAt }
 * 规则：仅 15 可发起（40941）；重复发起返回原 paymentNo（40943 防重复支付）
 * 金额由服务端生成，前端不传
 */
export function createPayment(orderNo, channel) {
  return request.post(`/order/${encodeURIComponent(orderNo)}/pay`, { channel })
}

/**
 * 查询支付结果 GET /api/order/{orderNo}/pay
 * 返回 { orderStatus, statusText, paymentNo, channel, amount, paidAt }
 * 前端在支付返回页轮询本接口，成功后刷新订单
 */
export function getPaymentResult(orderNo) {
  return request.get(`/order/${encodeURIComponent(orderNo)}/pay`)
}

// ================= 收货地址（C.2-01） =================
// 列表：默认地址排最前，其余按创建时间倒序；单客户最多 10 条（40952）
export function getAddressList() {
  return request.get('/address')
}

// 新增：receiver / phone / region / detail 四项必填（40925），手机号须 11 位数字
export function createAddress(data) {
  return request.post('/address', data)
}

// 修改：字段全部可选，传了才覆盖；非本人 40953、不存在 40951
export function updateAddress(addressId, data) {
  return request.put(`/address/${encodeURIComponent(addressId)}`, data)
}

// 删除：删默认地址时自动把最近创建的一条设为默认
export function deleteAddress(addressId) {
  return request.delete(`/address/${encodeURIComponent(addressId)}`)
}

// 设为默认：同客户其他地址默认标记自动清除
export function setDefaultAddress(addressId) {
  return request.put(`/address/${encodeURIComponent(addressId)}/default`)
}

// ================= 物流（C.2-04） =================
/**
 * 查询物流 GET /api/order/{orderNo}/logistics
 * 返回 { logisticsNo, carrier, status, statusText, traces: [{ time, desc }] }
 * status 枚举：SHIPPED 已发货 / IN_TRANSIT 运输中 / SIGNED 已签收
 * 未发货（状态 < 40）报 40971；订单只有一个物流单号，不拆包裹
 */
export function getLogistics(orderNo) {
  return request.get(`/order/${encodeURIComponent(orderNo)}/logistics`)
}

// ================= 售后（C.3） =================
/**
 * 发起售后 POST /api/after-sale（按商品维度，逐件申请）
 * 入参 { orderNo, productId, quantity, title, description, images }
 * 40962 订单状态不符（仅 40/50）、40963 数量超购买数、40964 已有进行中、
 * 40965 累计超 3 次、40968 图片超限
 */
export function createAfterSale(data) {
  return request.post('/after-sale', data)
}

// 我的售后单列表：参数 page + size（只能看到自己的）
export function getMyAfterSaleList(params = {}) {
  return request.get('/after-sale/my', {
    params: {
      page: Number(params.page) || 1,
      size: Number(params.size) || 10,
      // status 选填：后端支持按状态过滤时生效，不传即全部
      ...(params.status ? { status: params.status } : {})
    }
  })
}

// 售后单详情：买家只能看自己的（40967）
export function getAfterSaleDetail(afterSaleNo) {
  return request.get(`/after-sale/${encodeURIComponent(afterSaleNo)}`)
}

// 客户撤销售后：仅「待处理」可撤销（40966），撤销后不可恢复
export function cancelAfterSale(afterSaleNo) {
  return request.post(`/after-sale/${encodeURIComponent(afterSaleNo)}/cancel`)
}

// 售后图片上传：复用统一上传端点，返回图片 URL（最多 5 张、单张 ≤ 2MB、jpg/png/webp）
export async function uploadAfterSaleImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  const res = await request.post('/upload/image', formData)
  const url = typeof res === 'string' ? res : res?.url
  if (!url) return Promise.reject({ code: 50000, message: '图片上传失败' })
  return { url }
}

// ================= 已下线：购买意向（迭代 3 起由订单取代，仅保留兼容） =================
// 注意：该接口不做 mock，迭代 3 已无买家页面调用
export function submitIntent(data) {
  return request.post('/buyer/intent', data)
}
