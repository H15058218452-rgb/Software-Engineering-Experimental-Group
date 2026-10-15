export const INTENT_STATUS = {
  QUEUING: 0,
  TRADING: 1,
  SUCCESS: 2,
  VOID: 3,
  CANCELLED: 4,
  REQUEUED: 5,
  FINAL_FAILED: 6
}

export const INTENT_STATUS_TEXT = {
  0: '排队中',
  1: '交易中',
  2: '交易成功',
  3: '已作废',
  4: '已撤销',
  5: '重新排队中',
  6: '最终失败'
}