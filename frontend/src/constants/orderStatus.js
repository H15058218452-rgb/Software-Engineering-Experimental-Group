/* ============================================================
 * 订单状态（迭代 4 · 挑战需求 C：支付 / 配送 / 售后升级）
 * 口径来源：《迭代 4 用户故事与验收标准》第 0 节 + 《订单状态流转规则》
 *
 * 10 客户下单（内部态，对外不暴露）
 *  → 15 待支付 → 18 已支付 → 20 商家确认 → 30 备货完成 → 40 开始发货 → 50 交易完成（终态）
 *  15 / 18 / 20 / 30 → 90 已取消（终态）
 *
 * ⚠️ 与迭代 3 的差异：只在 10 与 20 之间插入 15、18，原有码值 10/20/30/40/50/90 不变。
 * ============================================================ */

export const ORDER_STATUS = {
  PENDING: 10,      // 客户下单（下单瞬间的内部态，同事务内即转 15）
  PENDING_PAY: 15,  // 待支付
  PAID: 18,         // 已支付
  CONFIRMED: 20,    // 商家确认
  READY: 30,        // 备货完成
  SHIPPED: 40,      // 开始发货
  COMPLETED: 50,    // 交易完成（终态）
  CANCELLED: 90     // 已取消（终态）
}

/* 状态文案兜底表。后端已返回 statusText 时优先用后端，不要自己映射码值 */
export const ORDER_STATUS_TEXT = {
  10: '客户下单',
  15: '待支付',
  18: '已支付',
  20: '商家确认',
  30: '备货完成',
  40: '开始发货',
  50: '交易完成',
  90: '已取消'
}

/* 客户可取消的状态（C.1-04）：15 / 18 / 20 / 30；「开始发货」40 及之后无取消入口 */
export const CUSTOMER_CANCELLABLE_STATUS = [15, 18, 20, 30]

/* 终态：50 / 90 不可再推进、不可再取消 */
export const ORDER_TERMINAL_STATUS = [50, 90]

/* 已支付状态：取消时需原路退款（前端用于二次确认文案） */
export const ORDER_PAID_STATUS = [18, 20, 30, 40, 50]

/* 物流入口开放状态（C.2-04）：开始发货 40 及之后 */
export const ORDER_LOGISTICS_STATUS = [40, 50]

/* 售后入口开放状态（C.3-01）：40 / 50；发货前退款统一走「取消订单」 */
export const ORDER_AFTER_SALE_STATUS = [40, 50]

export function isCustomerCancellable(status) {
  return CUSTOMER_CANCELLABLE_STATUS.includes(Number(status))
}

/* 待支付：订单页展示「去支付」，并展示 expireAt 倒计时 */
export function isAwaitingPayment(status) {
  return Number(status) === ORDER_STATUS.PENDING_PAY
}

/* 确认收货：只有 40 开始发货才可点（C.1-07） */
export function canReceiveOrder(status) {
  return Number(status) === ORDER_STATUS.SHIPPED
}

/* 物流入口：40 及之后显示（C.2-04） */
export function hasLogistics(status) {
  return ORDER_LOGISTICS_STATUS.includes(Number(status))
}

/* 售后入口：只在 40 / 50 显示（C.3-01） */
export function canApplyAfterSale(status) {
  return ORDER_AFTER_SALE_STATUS.includes(Number(status))
}

/* 是否已支付（取消需退款的原路提示用） */
export function isPaidStatus(status) {
  return ORDER_PAID_STATUS.includes(Number(status))
}

/* el-tag 配色：待支付 danger（需客户动作）、进行中 warning、交易完成 success、已取消 info */
export function orderStatusTagType(status) {
  const s = Number(status)
  if (s === ORDER_STATUS.COMPLETED) return 'success'
  if (s === ORDER_STATUS.CANCELLED) return 'info'
  if (s === ORDER_STATUS.PENDING_PAY) return 'danger'
  return 'warning'
}

/* 最终结果：终态才有值（COMPLETED / CANCELLED），非终态为 null */
export function finalResultText(finalResult) {
  if (finalResult === 'COMPLETED') return '交易完成'
  if (finalResult === 'CANCELLED') return '已取消'
  return '-'
}
