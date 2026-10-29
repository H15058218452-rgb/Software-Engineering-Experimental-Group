import axios from 'axios'
import { ERROR_MESSAGE } from '@/constants/errorCodes'
import { STORAGE_KEYS } from '@/constants/storageKeys'

function genRequestId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use(config => {
  config.headers['X-Request-Id'] = genRequestId()

  // 自动携带鉴权 Token（同时兼容买家和卖家）
  const sellerToken = localStorage.getItem(STORAGE_KEYS.SELLER_TOKEN)
  const buyerToken = localStorage.getItem(STORAGE_KEYS.BUYER_TOKEN)

  if (window.location.pathname.startsWith('/seller') && sellerToken) {
    config.headers['Authorization'] = `Bearer ${sellerToken}`
  } else if (buyerToken) {
    config.headers['Authorization'] = `Bearer ${buyerToken}`
  }

  return config
})

request.interceptors.response.use(
  res => {
    const { code, message, data } = res.data
    if (code === 0) return data

    // 401 处理
    if (code === 40101) {
      if (window.location.pathname.startsWith('/seller')) {
        localStorage.removeItem(STORAGE_KEYS.SELLER_TOKEN)
        window.location.href = '/seller/login'
      } else {
        localStorage.removeItem(STORAGE_KEYS.BUYER_TOKEN)
        window.location.href = '/buyer/login'
      }
    }

    return Promise.reject({
      code,
      message: message || ERROR_MESSAGE[code] || '请求失败'
    })
  },
  err => {
    const code = err?.response?.data?.code ?? 50000
    const msg = err?.response?.data?.message || ERROR_MESSAGE[code] || '网络请求失败，请检查网络后重试'
    return Promise.reject({ code, message: msg })
  }
)

export default request