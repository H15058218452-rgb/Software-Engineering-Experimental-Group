import request from '@/utils/request'

const success = (data, message = 'success') => ({ code: 0, message, data })

const genRequestId = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

const normalizeProduct = (product) => {
  if (!product) return null
  return {
    ...product,
    id: product.id == null ? product.id : String(product.id),
    image: product.imageUrl ?? (product.images && product.images.length > 0 ? product.images[0] : '') ?? product.image ?? '',
    price: product.price == null ? '' : String(product.price)
  }
}

// 1. 卖家登录
export function login(data) {
  return request.post('/seller/login', data)
}

// 2. 修改密码
export async function changePassword(data) {
  const result = await request.post('/seller/change-password', data)
  return success(result)
}

// 3. 获取所有商品列表
export async function getProductList(params) {
  const result = await request.get('/seller/products', { params })
  return success({
    list: (result?.list || result?.records || []).map(normalizeProduct),
    total: result?.total || 0
  })
}

// 4. 发布商品
export async function publishProduct(data) {
  const imageArr = data.images || (data.image ? [data.image] : []);

  const payload = {
    name: data.name?.trim(),
    description: data.description?.trim() || null,
    price: Number(data.price),
    stock: Number(data.stock),
    categoryId: data.categoryId, 
    images: imageArr, 
    requestId: genRequestId()
  }
  const result = await request.post('/product/publish', payload)
  return success(normalizeProduct(result), '发布成功')
}

// 5. 修改库存 (对接后端的 stock-adjust，计算 delta)
export async function updateStock(productId, newStock, oldStock) {
  const delta = Number(newStock) - Number(oldStock)
  
  const payload = { 
    productId: Number(productId),
    delta: delta,
    requestId: genRequestId() 
  }
  const result = await request.post('/product/stock-adjust', payload)
  return success(normalizeProduct(result), '库存修改成功')
}

// 6. 商品操作（B.1-5 商品状态只有两态：在售 / 已下架）
//    冻结、解冻、开始交易、确认成交/失败属于迭代 2 的「意向 + 队列 + 交易冻结」模型，
//    B 中意向机制已下线，这里不再提供这些动作。
const actionEndpoints = {
  'off-shelf': '/product/off-shelf',
  'shelf-on': '/product/shelf-on'
}

export async function operateProduct(action, data = {}) {
  const endpoint = actionEndpoints[action]
  if (!endpoint) throw { code: 40001, message: `不支持的商品操作：${action}` }

  const payload = {
    productId: Number(data.productId),
    requestId: genRequestId()
  }

  const result = await request.post(endpoint, payload)
  return success(normalizeProduct(result), '操作成功')
}

// 7. 意向列表：B 中购买意向已下线，前端不再提供入口，仅保留历史留档页使用
export async function getIntentList(productId) {
  const result = await request.get('/seller/intents', { params: { productId: String(productId) } })
  const list = Array.isArray(result) ? result : (result?.list ?? result?.records ?? [])
  return success({ ...(Array.isArray(result) ? {} : result), list, total: list.length })
}
export const getSellerIntents = getIntentList

// 8. 上传图片
export async function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  const result = await request.post('/upload/image', formData)
  const url = typeof result === 'string' ? result : result?.url
  if (!url) throw { code: 50000, message: '上传失败' }
  return success({ url }, '上传成功')
}

// 9. 获取分类树
export async function getCategoryTree() {
  const result = await request.get('/category/tree')
  return success(result)
}

// 10. 获取客户列表
export async function getCustomerList(params) {
  const result = await request.get('/seller/customers', { params })
  return success(result)
}

// 11. 该客户的历史意向（迭代 2 遗留，B 中已下线，仅只读留档；后端路径是 /intents）
export async function getCustomerHistory(customerId) {
  const result = await request.get(`/seller/customers/${customerId}/intents`)
  return success(result)
}

// 12. 订单管理（挑战需求 C：支付 / 配送升级后的 8 个状态）
// 状态码（后端 enums/OrderStatus）：
//   10 客户下单（内部态，对外不暴露） / 15 待支付 / 18 已支付
//   20 商家确认 / 30 备货完成 / 40 开始发货 / 50 交易完成 / 90 已取消
//
// 后端没有「卖家全局订单列表」（B.1-4 裁定）：商家只能从客户进入，看该客户的订单记录。
//   因此这里按客户查：GET /seller/customer/{customerId}/order（SellerOrderController）
export async function getCustomerOrderList(customerId, params = {}) {
  const result = await request.get(`/seller/customer/${encodeURIComponent(customerId)}/order`, {
    params: {
      page: Number(params.page) || 1,
      size: Number(params.size) || 10
    }
  })
  const list = Array.isArray(result) ? result : (result?.list ?? [])
  return success({
    list,
    total: Number(result?.total ?? list.length),
    page: Number(result?.page ?? params.page ?? 1),
    size: Number(result?.size ?? params.size ?? 10)
  })
}

// 状态推进（仅商家，C.1-06）：confirm 18→20、ready 20→30、ship 30→40
// 「交易完成」不在商家侧：40→50 只能由客户点「确认收货」（C.1-07），商家无 complete 权限
// 跳态 / 回退 / 重复提交 → 40921；50、90 终态 → 40923；非卖家 → 40922
const ORDER_ACTION_PATHS = {
  confirm: 'confirm',
  ready: 'ready',
  ship: 'ship'
}

export async function advanceOrder(orderNo, action, data = {}) {
  const path = ORDER_ACTION_PATHS[action]
  if (!path) throw { code: 40001, message: `不支持的订单操作：${action}` }

  // 发货必须带物流单号与快递公司（C.2-03），缺了直接在前端拦掉，不浪费一次请求
  let payload
  if (action === 'ship') {
    const logisticsNo = String(data.logisticsNo ?? '').trim()
    const carrier = String(data.carrier ?? '').trim()
    if (!logisticsNo || !carrier) throw { code: 40972, message: '物流单号与快递公司为必填项' }
    payload = { logisticsNo, carrier }
  }

  const result = await request.post(`/order/${encodeURIComponent(orderNo)}/${path}`, payload)
  return success(result, '操作成功')
}

// 查询物流（C.2-04）：订单 40 及之后可查；未发货报 40971
export async function getOrderLogistics(orderNo) {
  const result = await request.get(`/order/${encodeURIComponent(orderNo)}/logistics`)
  return success(result)
}

// 修改物流单号（C.2-03）：仅 40 开始发货、且客户未签收时可改
export async function updateOrderLogistics(orderNo, data = {}) {
  const logisticsNo = String(data.logisticsNo ?? '').trim()
  const carrier = String(data.carrier ?? '').trim()
  if (!logisticsNo || !carrier) throw { code: 40972, message: '物流单号与快递公司为必填项' }
  const result = await request.put(`/order/${encodeURIComponent(orderNo)}/logistics`, { logisticsNo, carrier })
  return success(result, '物流信息已更新')
}

// 商家取消（C.1-05）：15 / 18 / 20 / 30 / 40 可取消，50 之后无权限
//   15 取消不退款、回补库存；18/20/30 原路退款并回补库存；40 原路退款但库存不回补（货已发出）
//   退款由后端在同一事务里完成，前端只负责提示后果
export async function cancelOrder(orderNo, reason = '') {
  const payload = {}
  const text = String(reason ?? '').trim()
  if (text) payload.reason = text.slice(0, 100)
  const result = await request.post(`/order/${encodeURIComponent(orderNo)}/cancel`, payload)
  return success(result, '订单已取消')
}

// 13. 历史管理（已下架商品历史）
export async function getHistoryProducts(params) {
  const result = await request.get('/seller/history/products', { params })
  return success({
    list: result?.list ?? result?.records ?? [],
    total: result?.total || 0
  })
}

// 获取某个历史商品的历史意向记录
export async function getHistoryIntents(productId) {
  const result = await request.get(`/seller/history/products/${productId}/intents`)
  const list = Array.isArray(result) ? result : (result?.list ?? result?.records ?? [])
  return success(list)
}

// 14. 商品详情（编辑页回填）：GET /seller/products/{id} → SellerProductView
export async function getGoodsDetail(productId) {
  const result = await request.get(`/seller/products/${encodeURIComponent(productId)}`)
  return success(normalizeProduct(result))
}

// 14. 编辑商品：POST /product/update
//     后端 UpdateRequest 只收 名称/类别/描述/价格/图集，库存不在这里改（走 stock-adjust）
export async function updateGoods(productId, data) {
  const imageArr = data.images || (data.image ? [data.image] : [])

  const payload = {
    productId: Number(productId),
    name: data.name?.trim(),
    description: data.description?.trim() || null,
    price: Number(data.price),
    categoryId: data.categoryId,
    images: imageArr,
    requestId: genRequestId()
  }
  const result = await request.post('/product/update', payload)
  return success(normalizeProduct(result), '保存成功')
}

export const publishGoods = publishProduct

// 15. 售后管理（挑战需求 C.3）
// 售后单状态：PENDING 待处理 / WAIT_RETURN 待收货 / REFUNDED 已退款 / REJECTED 已拒绝 / CANCELLED 已撤销
// 单卖家模式：商家可看全部售后单，不存在按店铺隔离
export async function getAfterSaleList(params = {}) {
  const query = {
    page: Number(params.page) || 1,
    size: Number(params.size) || 10
  }
  if (params.status) query.status = String(params.status)

  const result = await request.get('/seller/after-sale', { params: query })
  const list = Array.isArray(result) ? result : (result?.list ?? result?.records ?? [])
  return success({
    list,
    total: Number(result?.total ?? list.length),
    page: Number(result?.page ?? query.page),
    size: Number(result?.size ?? query.size)
  })
}

// 售后单详情：买家只能看自己的，卖家可看全部（C.3-03）
export async function getAfterSaleDetail(afterSaleNo) {
  const result = await request.get(`/after-sale/${encodeURIComponent(afterSaleNo)}`)
  return success(result)
}

// 商家处理售后（C.3-04 ~ C.3-06）
//   agree-return 同意退货：PENDING → WAIT_RETURN，此时不退款、不回补库存
//   receive      确认收到退货并退款：WAIT_RETURN → REFUNDED，回补库存 + 原路退款
//   refund-only  仅退款：PENDING → REFUNDED，不要求寄回、库存不回补
//   reject       拒绝售后：PENDING → REJECTED，不退款、不回补库存
const AFTER_SALE_ACTION_PATHS = {
  'agree-return': 'agree-return',
  receive: 'receive',
  'refund-only': 'refund-only',
  reject: 'reject'
}

const AFTER_SALE_ACTION_TEXT = {
  'agree-return': '已同意退货',
  receive: '已确认收货并退款',
  'refund-only': '已退款',
  reject: '已拒绝售后'
}

export async function operateAfterSale(afterSaleNo, action, data = {}) {
  const path = AFTER_SALE_ACTION_PATHS[action]
  if (!path) throw { code: 40001, message: `不支持的售后操作：${action}` }

  // receive 可带退款备注；reject 可带拒绝理由（系统不强制、也不展示给客户）
  let payload
  if (action === 'receive') {
    const note = String(data.refundNote ?? '').trim()
    payload = note ? { refundNote: note.slice(0, 100) } : {}
  } else if (action === 'reject') {
    const reason = String(data.reason ?? '').trim()
    payload = reason ? { reason: reason.slice(0, 100) } : {}
  }

  const result = await request.post(
    `/seller/after-sale/${encodeURIComponent(afterSaleNo)}/${path}`,
    payload
  )
  return success(result, AFTER_SALE_ACTION_TEXT[action] || '操作成功')
}