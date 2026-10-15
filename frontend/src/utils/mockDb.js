/* ============================================================
 * 本地 mock 数据库(后端未连接前使用)
 * - localStorage 持久化,刷新/重启不丢(对应 Q14 历史永久保留)
 * - 联调后端后:api/seller.js 中 USE_MOCK 改 false,本文件可删
 * ============================================================ */

const KEY = 'mvp_mock_db_v1'

/* 本地占位图(SVG dataURI,不依赖外网图床,离线也能显示) */
const img = (t, color) =>
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200">` +
    `<rect width="200" height="200" fill="${color}"/>` +
    `<text x="100" y="108" font-size="18" fill="#ffffff" text-anchor="middle">${t}</text></svg>`
  )

/* 初始种子数据:3 件商品(已售/冻结/在线各一)+ 5 条意向,覆盖全部状态演示 */
const seed = () => ({
  seller: { username: 'admin', password: '123456' }, // ★ 演示账号

  goods: [
    {
      id: 1,
      name: '华为 Mate 70 Pro 9成新',
      price: 4999.00,
      description: '自用一年,无磕碰,电池健康90%,原装充电器齐全,支持当面验货。',
      images: [img(' Mate70 ', '#8c8c8c')],
      status: 3, // 已售
      createTime: '2025-06-01 10:00:00'
    },
    {
      id: 2,
      name: 'iPad Air 5 64G 蓝色',
      price: 3200.00,
      description: '国行在保,屏幕无划痕,带原装保护壳和类纸膜,仅同城面交。',
      images: [img(' iPad ', '#5b8ff9')],
      status: 2, // 冻结(交易中)
      createTime: '2025-06-10 09:30:00'
    },
    {
      id: 3,
      name: 'FreeBuds Pro 3 耳机',
      price: 899.00,
      description: '仅拆封试音,配件全,发票还在,支持当面验货,诚心出。',
      images: [img('耳机', '#61c07e')],
      status: 1, // 在线
      createTime: '2025-06-20 20:15:00'
    }
  ],

  intents: [
    { id: 1, goodsId: 1, buyerName: '张三', buyerPhone: '13800000001', message: '诚心要,可面交', status: 2, createTime: '2025-06-02 12:00:00' }, // 交易成功
    { id: 2, goodsId: 1, buyerName: '李四', buyerPhone: '13800000002', message: '价格可议',       status: 4, createTime: '2025-06-02 15:00:00' }, // 已失效(商品已售)
    { id: 3, goodsId: 2, buyerName: '王五', buyerPhone: '13800000003', message: '今晚就能交易',   status: 1, createTime: '2025-06-11 08:00:00' }, // 交易中(队首)
    { id: 4, goodsId: 2, buyerName: '赵六', buyerPhone: '13800000004', message: '排队等递补',     status: 0, createTime: '2025-06-11 09:00:00' }, // 排队中
    { id: 5, goodsId: 3, buyerName: '孙七', buyerPhone: '13800000005', message: '包邮吗',         status: 0, createTime: '2025-06-21 10:00:00' }  // 排队中
  ],

  nextGoodsId: 4,   // 自增主键
  nextIntentId: 6
})

/* 读取数据库;首次访问或数据损坏时写入种子数据 */
export const loadDb = () => {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) return JSON.parse(raw)
  } catch (e) { /* 数据损坏则重建 */ }
  const db = seed()
  saveDb(db)
  return db
}

/* 持久化保存 */
export const saveDb = (db) => {
  try {
    localStorage.setItem(KEY, JSON.stringify(db))
  } catch (e) { /* 超出配额等异常忽略 */ }
}

/* 重置为种子数据(演示用,可在控制台执行:import('@/utils/mockDb').then(m => m.resetDb())) */
export const resetDb = () => {
  const db = seed()
  saveDb(db)
  return db
}

/* ---------- 状态字典:与"商品状态与后台按钮对照表"一致 ---------- */
/* 商品:1 在线(绿) 2 冻结(黄) 3 已售(灰) 4 下架(灰) */
export const GOODS_STATUS = { 1: '在线', 2: '冻结', 3: '已售', 4: '下架' }

/* 意向:0 排队中 1 交易中 2 交易成功 3 交易失败 4 已失效(置灰) */
export const INTENT_STATUS = { 0: '排队中', 1: '交易中', 2: '交易成功', 3: '交易失败', 4: '已失效' }