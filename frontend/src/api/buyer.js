import request from '@/utils/request'

// ================= 账号相关 =================
// 买家登录
export function login(data) {
  return request.post('/buyer/auth/login', data)
}

// 买家注册
export function register(data) {
  return request.post('/buyer/auth/register', data)
}

// ================= 商品相关 =================
// 获取商品列表（支持分页、分类筛选、关键词搜索）
// 参数示例: { page: 1, pageSize: 10, categoryId: '123', keyword: '手机' }
export function getProductList(params) {
  return request.get('/buyer/products', { params })
}

// 获取商品详情（多商品模式下，需要传具体的商品ID）
export function getProductDetail(id) {
  return request.get(`/buyer/products/${id}`)
}

// 获取商品分类（用于买家的两级级联筛选）
export function getCategories() {
  return request.get('/buyer/categories')
}

// ================= 意向与订单相关 =================
// 提交购买意向（只传商品ID，身份由Token自动识别）
export function submitIntent(productId) {
  return request.post('/buyer/intent', { productId })
}

// 获取“我的历史订单”记录
// 参数示例: { page: 1, pageSize: 10 }
export function getMyOrders(params) {
  return request.get('/buyer/orders', { params })
}