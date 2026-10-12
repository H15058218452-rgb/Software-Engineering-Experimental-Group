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

  const token = localStorage.getItem(STORAGE_KEYS.SELLER_TOKEN)
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }

  // 口令码：优先使用请求头里已设置的口令码，否则从 localStorage 取
  if (config.headers['X-Skip-Intent-Code'] === 'true') {
    delete config.headers['X-Skip-Intent-Code']
  } else if (!config.headers['X-Intent-Code']) {
    const intentCode = localStorage.getItem(STORAGE_KEYS.INTENT_CODE)
    if (intentCode) {
      config.headers['X-Intent-Code'] = intentCode
    }
  }

  return config
})

request.interceptors.response.use(
  res => {
    const { code, message, data } = res.data
    if (code === 0) return data

    if (code === 40101 && window.location.pathname.startsWith('/seller')) {
      localStorage.removeItem(STORAGE_KEYS.SELLER_TOKEN)
      window.location.href = '/seller/login'
    }

    return Promise.reject({
      code,
      message: message || ERROR_MESSAGE[code] || '请求失败'
    })
  },
  err => {
    const code = err?.response?.data?.code ?? 50000
    const msg =
      err?.response?.data?.message ||
      ERROR_MESSAGE[code] ||
      '网络请求失败，请检查网络后重试'
    return Promise.reject({ code, message: msg })
  }
)

export default request