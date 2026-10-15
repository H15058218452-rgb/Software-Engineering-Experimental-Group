import request from '@/utils/request'

// 兼容现有页面：request.js 已经把成功响应解包，
// 这里重新包装为旧页面使用的 { code, message, data } 格式。
const success = (data, message = 'success') => ({
  code: 0,
  message,
  data
})

// 后端写接口需要 requestId 放在请求体中。
// request.js 里的 X-Request-Id 请求头仍然保留。
const genRequestId = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

// 将后端商品字段适配为现有页面使用的字段。
// 保留原字段，不改变后端返回的数据结构。
const normalizeProduct = (product) => {
  if (!product) return null

  return {
    ...product,
    id: product.id == null ? product.id : String(product.id),
    image: product.imageUrl ?? product.image ?? '',
    price: product.price == null ? '' : String(product.price)
  }
}

// 1. 卖家登录
// 直接返回后端数据，避免改变现有登录页面的行为。
export function login(data) {
  return request.post('/seller/login', data)
}

// 2. 修改密码
export async function changePassword(data) {
  const result = await request.post('/seller/change-password', data)
  return success(result)
}

// 3. 获取当前商品
export async function getCurrentProduct() {
  const result = await request.get('/seller/product/current')
  return success(normalizeProduct(result))
}

// 4. 发布商品
export async function publishProduct(data) {
  const payload = {
    name: data.name?.trim(),
    description: data.description?.trim() || null,
    price: String(data.price),
    imageUrl: data.imageUrl ?? data.image ?? null,
    requestId: genRequestId()
  }

  const result = await request.post('/product/publish', payload)
  return success(normalizeProduct(result), '发布成功')
}

export const publishGoods = publishProduct

// 5. 编辑商品
// 当前后端接口契约规定商品发布后不可修改，不能再模拟保存成功。
export function updateGoods() {
  return Promise.reject({
    code: 40001,
    message: '商品发布后不可修改，请勿使用编辑功能'
  })
}

// 6. 获取商品详情
// 当前契约未提供单独的商品详情接口。
export function getGoodsDetail() {
  return Promise.reject({
    code: 40001,
    message: '当前后端未提供商品详情接口'
  })
}

// 7. 商品操作
const actionEndpoints = {
  freeze: '/product/freeze',
  unfreeze: '/product/unfreeze',
  'start-trade': '/product/start-trade',
  'confirm-sold': '/product/confirm-sold',
  'confirm-failed': '/product/confirm-failed',
  'off-shelf': '/product/off-shelf'
}

export async function operateProduct(action, data = {}) {
  const endpoint = actionEndpoints[action]

  if (!endpoint) {
    throw {
      code: 40001,
      message: `不支持的商品操作：${action}`
    }
  }

  const payload = {
    productId: String(data.productId),
    requestId: genRequestId()
  }

  // 成交接口必须提供当前交易意向 ID。
  if (action === 'confirm-sold') {
    if (data.intentId == null) {
      throw {
        code: 40001,
        message: '确认成交需要提供当前交易意向 ID'
      }
    }
    payload.intentId = String(data.intentId)
  }

  // 交易失败必须指定作废或重新排队。
  if (action === 'confirm-failed') {
    if (!['VOID', 'REQUEUE'].includes(data.disposition)) {
      throw {
        code: 40001,
        message: '请指定交易失败后的处理方式'
      }
    }

    if (data.intentId == null) {
      throw {
        code: 40001,
        message: '确认交易失败需要提供当前交易意向 ID'
      }
    }

    payload.intentId = String(data.intentId)
    payload.disposition = data.disposition
  }

  const result = await request.post(endpoint, payload)
  return success(normalizeProduct(result), '操作成功')
}

// 8. 历史商品
export async function getHistoryProducts(page = 1, pageSize = 10) {
  const result = await request.get('/seller/history/products', {
    params: { page, pageSize }
  })

  const list = Array.isArray(result)
    ? result
    : (result?.list ?? result?.records ?? [])

  return success({
    ...(Array.isArray(result) ? {} : result),
    list: list.map(normalizeProduct),
    total: Array.isArray(result)
      ? list.length
      : (result?.total ?? list.length)
  })
}

// 9. 历史商品的意向记录
export async function getHistoryProductIntents(id) {
  const result = await request.get(
    `/seller/history/products/${encodeURIComponent(String(id))}/intents`
  )
  return success(result)
}

// 10. 获取意向列表
export async function getIntentList(productId) {
  const result = await request.get('/seller/intents', {
    params: { productId: String(productId) }
  })

  const list = Array.isArray(result)
    ? result
    : (result?.list ?? result?.records ?? [])

  return success({
    ...(Array.isArray(result) ? {} : result),
    list,
    total: Array.isArray(result)
      ? list.length
      : (result?.total ?? list.length)
  })
}

export const getSellerIntents = getIntentList

// 11. 上传商品图片
export async function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)

  const result = await request.post('/upload/image', formData)

  // 兼容后端返回 { url: '/uploads/xxx' } 或直接返回 URL 字符串。
  const url = typeof result === 'string'
    ? result
    : result?.url

  if (!url) {
    throw {
      code: 50000,
      message: '图片上传成功响应中没有图片地址'
    }
  }

  return success({ url }, '上传成功')
}

// 以下是旧页面可能引用的兼容导出。
// 当前后端契约没有提供删除商品或切换状态接口，不能伪造成功。
export function deleteGoods() {
  return Promise.reject({
    code: 40001,
    message: '当前后端未提供删除商品接口，请使用下架功能'
  })
}

export function toggleGoodsStatus() {
  return Promise.reject({
    code: 40001,
    message: '请使用冻结、解冻或下架接口'
  })
}