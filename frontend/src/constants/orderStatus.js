/* ============================================================
 * 订单状态（迭代 3 · 挑战需求 B）
 * 口径来源：《订单状态流转 · 口径裁定》状态枚举
 * 10 客户下单 → 20 商家确认 → 30 备货完成 → 40 开始发货 → 50 交易完成
 * 10/20/30/40 可取消 → 90 已取消（终态）
 * ============================================================ */

export const ORDER_STATUS = {
  PENDING: 10,    // 客户下单
  CONFIRMED: 20,  // 商家确认
  READY: 30,      // 备货完成
  SHIPPED: 40,    // 开始发货
  COMPLETED: 50,  // 交易完成（终态）
  CANCELLED: 90   // 已取消（终态）
}

/* 状态文案兜底表。后端已返回 statusText 时优先用后端，不要自己映射码值 */
export const ORDER_STATUS_TEXT = {
  10: '客户下单',
  20: '商家确认',
  30: '备货完成',
  40: '开始发货',
  50: '交易完成',
  90: '已取消'
}

/* 客户可取消的状态：「开始发货」之前，即 10 / 20 / 30；40 及以后客户不可取消 */
export const CUSTOMER_CANCELLABLE_STATUS = [10, 20, 30]

/* 终态：50 / 90 不可再推进、不可再取消 */
export const ORDER_TERMINAL_STATUS = [50, 90]

export function isCustomerCancellable(status) {
  return CUSTOMER_CANCELLABLE_STATUS.includes(Number(status))
}

/* el-tag 配色：进行中 warning、交易完成 success、已取消 info */
export function orderStatusTagType(status) {
  const s = Number(status)
  if (s === ORDER_STATUS.COMPLETED) return 'success'
  if (s === ORDER_STATUS.CANCELLED) return 'info'
  return 'warning'
}

/* 最终结果：终态才有值（COMPLETED / CANCELLED），非终态为 null */
export function finalResultText(finalResult) {
  if (finalResult === 'COMPLETED') return '交易完成'
  if (finalResult === 'CANCELLED') return '已取消'
  return '-'
}
