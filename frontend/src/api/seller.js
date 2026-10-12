import request from '@/utils/request'

// 登录（Mock 版，后端好了再换成 request.post）
export function login(data) {
  return new Promise((resolve) => {
    setTimeout(() => {
      if (data.password === '123456') {
        resolve({ code: 0, message: 'success', data: { token: 'mock-token-123', seller: { name: '卖家' } } })
      } else {
        resolve({ code: 40101, message: '账号或密码错误', data: null })
      }
    }, 800)
  })
}

// 修改密码
export function changePassword(data) {
  return request.post('/seller/change-password', data)
}

// 获取当前商品
export function getCurrentProduct() {
  return request.get('/seller/product/current')
}

// 发布商品
export function publishProduct(data) {
  return request.post('/product/publish', data)
}

// 商品操作（冻结、解冻、开始交易、确认成功、确认失败、下架）
export function operateProduct(action, data) {
  return request.post(`/product/${action}`, data)
}

// 历史商品
export function getHistoryProducts() {
  return request.get('/seller/history/products')
}

// 意向列表
export function getIntentList(productId) {
  return request.get(`/seller/intents?productId=${productId}`)
}

// 图片上传
export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}