/* ============================================================
 * 售后状态与类型（迭代 4 · C.3 售后服务升级）
 * 接口规范第六节：
 *   状态 PENDING 待处理 / WAIT_RETURN 待收货 / REFUNDED 已退款
 *        REJECTED 已拒绝 / CANCELLED 已撤销
 *   类型 RETURN_REFUND 退货退款 / REFUND_ONLY 仅退款
 * ============================================================ */

export const AFTER_SALE_STATUS = {
  PENDING: 'PENDING',
  WAIT_RETURN: 'WAIT_RETURN',
  REFUNDED: 'REFUNDED',
  REJECTED: 'REJECTED',
  CANCELLED: 'CANCELLED'
}

export const AFTER_SALE_STATUS_TEXT = {
  PENDING: '待处理',
  WAIT_RETURN: '待收货',
  REFUNDED: '已退款',
  REJECTED: '已拒绝',
  CANCELLED: '已撤销'
}

export const AFTER_SALE_TYPE_TEXT = {
  RETURN_REFUND: '退货退款',
  REFUND_ONLY: '仅退款'
}

/* 进行中：同一商品同时只允许一个（40964） */
export const AFTER_SALE_ONGOING_STATUS = [
  AFTER_SALE_STATUS.PENDING,
  AFTER_SALE_STATUS.WAIT_RETURN
]

/* 状态筛选下拉（买家列表用） */
export const AFTER_SALE_STATUS_OPTIONS = Object.entries(AFTER_SALE_STATUS_TEXT).map(
  ([value, label]) => ({ value, label })
)

export function afterSaleStatusText(status, statusText) {
  return statusText || AFTER_SALE_STATUS_TEXT[status] || '-'
}

export function isAfterSaleOngoing(status) {
  return AFTER_SALE_ONGOING_STATUS.includes(status)
}

/* 客户撤销售后：仅「待处理」可撤销（C.3-07 / 40966） */
export function isAfterSaleCancellable(status) {
  return status === AFTER_SALE_STATUS.PENDING
}

export function afterSaleStatusTagType(status) {
  if (status === AFTER_SALE_STATUS.REFUNDED) return 'success'
  if (status === AFTER_SALE_STATUS.REJECTED) return 'danger'
  if (status === AFTER_SALE_STATUS.CANCELLED) return 'info'
  return 'warning'
}

/* 售后图片限制：最多 5 张、单张 ≤ 2MB、仅 jpg / png / webp（40968） */
export const AFTER_SALE_IMAGE_RULE = {
  maxCount: 5,
  maxSizeMB: 2,
  accept: ['image/jpeg', 'image/jpg', 'image/png', 'image/webp']
}

/* 标题 / 描述长度上限 */
export const AFTER_SALE_TITLE_MAX = 50
export const AFTER_SALE_DESC_MAX = 500

/* 同一商品累计售后次数上限（含已拒绝、已撤销、已退款） */
export const AFTER_SALE_MAX_TIMES = 3
