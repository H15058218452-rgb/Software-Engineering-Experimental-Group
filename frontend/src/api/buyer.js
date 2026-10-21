import request from '@/utils/request'
import { STORAGE_KEYS } from '@/constants/storageKeys' 
// 当前商品（买家视角）
export function getCurrentProduct() {
  return request.get('/buyer/product/current')
}
// 提交购买意向
export function submitIntent(data) {
  return request.post('/buyer/intent', data, {
    headers: {
      'X-Skip-Intent-Code': 'true'
    }
  })
}

// 查询意向：传入买家输入的口令码
export function queryIntent(code) {
  return request.get('/buyer/intent', {
    params: { code }
  })
}
// 修改姓名、电话
export function updateIntent(data) {
  const code = localStorage.getItem(STORAGE_KEYS.INTENT_CODE)
  return request.put('/buyer/intent', { ...data, code })
}

// 撤销意向
export function cancelIntent() {
  const code = localStorage.getItem(STORAGE_KEYS.INTENT_CODE)
  return request.post('/buyer/intent/cancel', { code })
}