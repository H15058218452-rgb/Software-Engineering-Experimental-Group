/* ============================================================
 * 未登录临时加购（B.2-2 组内拟定：未登录加购，登录后自动恢复到账号购物车）
 * 只存 productId + quantity，不存价格/名称，登录后按接口重新校验
 * ============================================================ */
import { STORAGE_KEYS } from '@/constants/storageKeys'

const KEY = STORAGE_KEYS.PENDING_CART
const MAX_LINES = 50 // 购物车最多 50 条
const MAX_QTY = 99 // 单条数量上限

/** 读取临时加购清单 */
export function readPendingCart() {
  try {
    const raw = localStorage.getItem(KEY)
    const list = raw ? JSON.parse(raw) : []
    return Array.isArray(list) ? list : []
  } catch (e) {
    return []
  }
}

/** 写入临时加购清单 */
function savePendingCart(list) {
  try {
    localStorage.setItem(KEY, JSON.stringify(list))
  } catch (e) {
    /* 忽略配额异常 */
  }
}

/**
 * 记录一条临时加购
 * - 同一商品累加数量（与后端「同商品已在车内则累加」一致）
 * - 单条不超过 99、总条数不超过 50
 * @returns {{ok: boolean, reason?: string}}
 */
export function pushPendingCart(productId, quantity = 1) {
  const id = String(productId)
  const qty = Math.min(Math.max(Number(quantity) || 1, 1), MAX_QTY)
  const list = readPendingCart()
  const exist = list.find(item => String(item.productId) === id)

  if (exist) {
    exist.quantity = Math.min(exist.quantity + qty, MAX_QTY)
  } else {
    if (list.length >= MAX_LINES) {
      return { ok: false, reason: `购物车最多 ${MAX_LINES} 条，登录后可先清理后再加购` }
    }
    list.push({ productId: id, quantity: qty })
  }

  savePendingCart(list)
  return { ok: true }
}

/** 清空临时加购清单 */
export function clearPendingCart() {
  localStorage.removeItem(KEY)
}
