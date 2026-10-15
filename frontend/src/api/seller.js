import request from '@/utils/request'

// 全局 Mock 工具：模拟网络延迟
const mockDelay = (ms = 500) => new Promise(resolve => setTimeout(resolve, ms))

// ==================== 接口 ====================

// 1. 登录
export function login(data) {
  return new Promise((resolve) => {
    setTimeout(() => {
      if (data.password === '123456') {
        resolve({ code: 0, message: 'success', data: { token: 'mock-token-123', seller: { username: '卖家' } } })
      } else {
        resolve({ code: 40101, message: '账号或密码错误', data: null })
      }
    }, 800)
  })
}

// 2. 修改密码
export function changePassword(data) {
  return mockDelay().then(() => ({ code: 0, message: 'success', data: null }))
}

// 3. 获取当前商品
export function getCurrentProduct() {
  return mockDelay().then(() => ({
    code: 0,
    message: 'success',
    data: {
      id: 1,
      name: '华为 Mate 70 Pro',
      description: '超聚光微距长焦，超高速风驰闪拍。',
      price: '6999.00',
      image: 'https://via.placeholder.com/300/f5f5f5/999999?text=手机',
      status: 0 // 0:在售, 1:手动冻结, 2:交易中冻结, 3:已下架
    }
  }))
}

// 4. 发布商品（同时提供 publishProduct 和 publishGoods 两个名字，保证页面导入不报错）
export function publishProduct(data) {
  return mockDelay().then(() => ({ code: 0, message: '发布成功', data: null }))
}
export const publishGoods = publishProduct

// 5. 编辑商品（补齐 PublishGoods.vue 里用到的 updateGoods）
export function updateGoods(id, data) {
  return mockDelay().then(() => ({ code: 0, message: '修改成功', data: null }))
}

// 6. 获取商品详情（补齐 PublishGoods.vue 里用到的 getGoodsDetail）
export function getGoodsDetail(id) {
  return mockDelay().then(() => ({
    code: 0,
    message: 'success',
    data: { id, name: '旧商品', price: '100.00', stock: 1, category: '手机', image: '', description: '' }
  }))
}

// 7. 商品操作（冻结、解冻、开始交易、确认成功、确认失败、下架）
export function operateProduct(action, data) {
  return mockDelay().then(() => ({ code: 0, message: '操作成功', data: null }))
}

// 8. 历史商品列表
export function getHistoryProducts() {
  const mockList = [
    { id: 2, name: '苹果 iPhone 16 Pro', price: '8999.00', status: 3, image: 'https://via.placeholder.com/150/f5f5f5/999999?text=手机' },
    { id: 3, name: '小米 15 Ultra', price: '5999.00', status: 3, image: 'https://via.placeholder.com/150/f5f5f5/999999?text=手机' }
  ]
  return mockDelay().then(() => ({
    code: 0,
    message: 'success',
    data: { list: mockList, total: mockList.length }
  }))
}

// 意向列表（补齐 createTime 和 queuePosition）
export function getIntentList(productId) {
  const mockIntents = [
    { id: 1, queuePosition: 1, buyerName: '张三', buyerPhone: '13800001111', remark: '想要', intentStatus: 0, createTime: '2026-10-13 10:00' },
    { id: 2, queuePosition: 2, buyerName: '李四', buyerPhone: '13900002222', remark: '排队', intentStatus: 0, createTime: '2026-10-13 10:05' }
  ]
  return mockDelay().then(() => ({ code: 0, message: 'success', data: { list: mockIntents, total: mockIntents.length } }))
}
export const getSellerIntents = getIntentList

// 10. 图片上传
export function uploadImage(file) {
  return mockDelay().then(() => ({
    code: 0,
    message: '上传成功',
    data: { url: 'https://via.placeholder.com/150/f5f5f5/999999?text=新图' }
  }))
}

// 11. 商品删除 / 上下架（GoodsList.vue 备用）
export function deleteGoods(id) {
  return mockDelay().then(() => ({ code: 0, message: '删除成功', data: null }))
}
export function toggleGoodsStatus(id, status) {
  return mockDelay().then(() => ({ code: 0, message: '操作成功', data: null }))
}