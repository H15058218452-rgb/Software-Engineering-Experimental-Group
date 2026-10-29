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

// 6. 商品操作（冻结、解冻、开始交易、确认成交/失败、下架、上架）
const actionEndpoints = {
  freeze: '/product/freeze',
  unfreeze: '/product/unfreeze',
  'start-trade': '/product/start-trade',
  'confirm-sold': '/product/confirm-sold',
  'confirm-failed': '/product/confirm-failed',
  'off-shelf': '/product/off-shelf',
  'shelf-on': '/product/shelf-on' // 👈 补充：重新上架接口
}

export async function operateProduct(action, data = {}) {
  const endpoint = actionEndpoints[action]
  if (!endpoint) throw { code: 40001, message: `不支持的商品操作：${action}` }

  const payload = {
    productId: Number(data.productId),
    requestId: genRequestId()
  }

  if (action === 'confirm-sold' || action === 'confirm-failed') {
    if (data.intentId == null) throw { code: 40001, message: '需要提供意向 ID' }
    payload.intentId = Number(data.intentId)
    if (action === 'confirm-failed') {
      if (!['VOID', 'REQUEUE'].includes(data.disposition)) throw { code: 40001, message: '请指定处置方式' }
      payload.disposition = data.disposition
    }
  }

  const result = await request.post(endpoint, payload)
  return success(normalizeProduct(result), '操作成功')
}

// 7. 获取意向列表
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

// 10. 获取客户列表（Mock）
export async function getCustomerList(params) {
  return new Promise(resolve => {
    setTimeout(() => {
      resolve(success({
        list: [
          { id: '101', username: '张三', phone: '13800001111', address: '图书馆', registerTime: '2026-10-01T10:00:00' },
          { id: '102', username: '李四', phone: '13900002222', address: '食堂', registerTime: '2026-10-02T11:00:00' }
        ],
        total: 2
      }))
    }, 300)
  })
}

// 11. 获取客户历史订单（Mock）
export async function getCustomerHistory(customerId) {
  return new Promise(resolve => {
    setTimeout(() => {
      resolve(success([
        { id: '1', productName: '盲盒', orderTime: '2026-10-20T12:00:00', result: '交易成功', resultTime: '2026-10-20T14:00:00' },
        { id: '2', productName: '键盘', orderTime: '2026-10-21T09:00:00', result: '排队中', resultTime: null }
      ]))
    }, 300)
  })
}

// 兼容旧代码空壳
export function updateGoods() { return Promise.reject({ code: 40001, message: '暂不支持编辑' }) }
export function getGoodsDetail() { return Promise.reject({ code: 40001, message: '暂无详情接口' }) }
export const publishGoods = publishProduct;