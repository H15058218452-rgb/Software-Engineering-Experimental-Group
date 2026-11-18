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

// 12. 订单管理（挑战需求 B.1）
// 订单状态码（后端 enums/OrderStatus）：10 客户下单 / 20 商家确认 / 30 备货完成 / 40 开始发货 / 50 交易完成 / 90 已取消
//
// 后端只有 9 个订单端点，没有「卖家全局订单列表」：
//   B.1-4 已裁定 —— 商家不设全局订单列表、不按状态筛选，只能从客户进入，看该客户的订单记录。
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

// 状态推进（仅商家）：10→20 confirm、20→30 ready、30→40 ship、40→50 complete
// 跳态 / 回退 / 重复提交 → 40921；50、90 终态 → 40923；非卖家 → 40301/40101
const ORDER_ACTION_PATHS = {
  confirm: 'confirm',
  ready: 'ready',
  ship: 'ship',
  complete: 'complete'
}

export async function advanceOrder(orderNo, action) {
  const path = ORDER_ACTION_PATHS[action]
  if (!path) throw { code: 40001, message: `不支持的订单操作：${action}` }
  const result = await request.post(`/order/${encodeURIComponent(orderNo)}/${path}`)
  return success(result, '操作成功')
}

// 商家取消订单：10 / 20 / 30 / 40 可取消（50 之后隐藏按钮）；取消后同事务回补库存
export async function cancelOrder(orderNo) {
  const result = await request.post(`/order/${encodeURIComponent(orderNo)}/cancel`)
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