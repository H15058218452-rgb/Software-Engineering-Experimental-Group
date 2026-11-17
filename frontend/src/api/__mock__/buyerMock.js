/* ============================================================================
 * 🧪 买家端 Mock 数据（仅前端自测用 · 联调时请整体删除）
 * ----------------------------------------------------------------------------
 * 作用：后端接口没好之前，把买家端页面全部跑通（商品列表 / 详情 / 购物车 /
 *      收藏 / 我的订单 / 下单 / 取消订单 / 缺货标红），不需要起后端。
 *
 * 怎么关掉（三选一，推荐第一种）：
 *   1) 把下面 MOCK_ENABLED 改成 false      → 立刻恢复走真实后端
 *   2) 删掉本 __mock__ 目录 + api/buyer.js 里所有带 MOCK_ENABLED 的行
 *   3) 全局搜索 "MOCK_ENABLED" 即可定位全部 mock 分支
 *
 * 数据存在浏览器 localStorage 的 __mock_buyer_db__ 里，刷新 / 切页面都不丢；
 * 想恢复初始数据：控制台执行 __mockReset()
 * 想测失败场景：改下面 MOCK_CONFIG 里的开关（改完刷新页面）
 * ========================================================================== */

import { ERROR_MESSAGE } from '@/constants/errorCodes'

/* ---------------------------- 总开关 ---------------------------- */
export const MOCK_ENABLED = true

/* ------------------ 场景开关（改完刷新页面生效） ------------------ */
export const MOCK_CONFIG = {
  delay: 200,              // 模拟网络延迟（毫秒），设 0 就是瞬间返回
  simulateShortage: false, // true → 结算必报 40924，用来看「缺货标红 + 取消勾选」
  emptyCart: false,        // true → 购物车返回空（看空态）
  emptyFavorites: false,   // true → 收藏返回空（看空态）
  emptyOrders: false       // true → 订单返回空（看空态）
}

/* ============================================================================
 * 一、静态数据：分类 / 商品
 * ========================================================================== */

// 用 dev server 自己托管图片，离线也能显示（文件在 public/mock/p1~p6.svg）
const IMG = n => `${window.location.origin}/mock/p${n}.svg`

const CATEGORIES = [
  {
    id: '1',
    name: '数码电子',
    children: [
      { id: '11', name: '手机' },
      { id: '12', name: '电脑外设' },
      { id: '13', name: '耳机音箱' }
    ]
  },
  {
    id: '2',
    name: '运动户外',
    children: [
      { id: '21', name: '球类' },
      { id: '22', name: '鞋靴' }
    ]
  },
  {
    id: '3',
    name: '家居生活',
    children: [
      { id: '31', name: '厨房用品' },
      { id: '32', name: '床品家纺' }
    ]
  }
]

// status：0 = 在售，1 = 已下架（迭代 3 只有这两态）
// stock：库存，0 表示售罄（列表仍然展示，详情页加购按钮置灰）
const PRODUCTS = [
  {
    id: '1', name: '篮球 标准 7 号 室内外通用', price: '120.00', stock: 50, status: 0,
    categoryId: '21', categoryName: '球类', image: IMG(1),
    description: '<p>标准 7 号篮球，室内外通用，PU 材质手感细腻，耐磨抗打。</p><p>适合日常训练与业余比赛使用。</p>'
  },
  {
    id: '2', name: '足球 5 号 耐磨训练用球', price: '98.00', stock: 30, status: 0,
    categoryId: '21', categoryName: '球类', image: IMG(2),
    description: '<p>5 号标准足球，机缝工艺，耐磨 PVC 表皮，适合草地与水泥地训练。</p>'
  },
  {
    id: '3', name: '跑步鞋 男款 缓震回弹', price: '399.00', stock: 20, status: 0,
    categoryId: '22', categoryName: '鞋靴', image: IMG(3),
    description: '<p>轻量缓震中底，透气网面鞋身，日常通勤与慢跑都合适。</p><p>尺码偏正常，建议按平时运动鞋尺码选购。</p>'
  },
  {
    id: '4', name: '机械键盘 87 键 青轴', price: '279.00', stock: 15, status: 0,
    categoryId: '12', categoryName: '电脑外设', image: IMG(4),
    description: '<p>87 键紧凑布局，青轴段落感清晰，适合办公与游戏。</p><p>支持有线连接，键线分离设计。</p>'
  },
  {
    id: '5', name: '无线鼠标 静音办公', price: '89.00', stock: 40, status: 0,
    categoryId: '12', categoryName: '电脑外设', image: IMG(5),
    description: '<p>2.4G 无线连接，静音微动，长时间办公不扰人。</p>'
  },
  {
    id: '6', name: '蓝牙耳机 Pro 3 主动降噪', price: '899.00', stock: 8, status: 0,
    categoryId: '13', categoryName: '耳机音箱', image: IMG(6),
    description: '<p>主动降噪，单次续航 6 小时，配合充电盒可用 30 小时。</p><p>支持双设备连接与低延迟模式。</p>'
  },
  {
    id: '7', name: '蓝牙音箱 便携防水', price: '349.00', stock: 0, status: 0,
    categoryId: '13', categoryName: '耳机音箱', image: IMG(1),
    description: '<p>IPX7 防水，续航 12 小时，户外露营好搭档。（当前库存为 0，用于测「已售罄」）</p>'
  },
  {
    id: '8', name: '智能手表 经典款（已下架示例）', price: '1099.00', stock: 12, status: 1,
    categoryId: '13', categoryName: '耳机音箱', image: IMG(2),
    description: '<p>这件商品用来测「已下架」：列表接口会过滤掉它，详情页会提示无法购买。</p>'
  },
  {
    id: '9', name: '智能手机 6.5 英寸 大电池', price: '2299.00', stock: 25, status: 0,
    categoryId: '11', categoryName: '手机', image: IMG(3),
    description: '<p>6.5 英寸高刷屏，5000mAh 大电池，日常使用两天一充。</p>'
  },
  {
    id: '10', name: '不锈钢汤锅 24cm 带盖', price: '168.00', stock: 60, status: 0,
    categoryId: '31', categoryName: '厨房用品', image: IMG(4),
    description: '<p>304 不锈钢加厚锅体，燃气与电磁炉通用。</p>'
  },
  {
    id: '11', name: '纯棉四件套 1.8m 床', price: '259.00', stock: 35, status: 0,
    categoryId: '32', categoryName: '床品家纺', image: IMG(5),
    description: '<p>100% 全棉，亲肤透气，包含被套 1、床单 1、枕套 2。</p>'
  },
  {
    id: '12', name: '保温杯 500ml 长效保温', price: '79.00', stock: 100, status: 0,
    categoryId: '31', categoryName: '厨房用品', image: IMG(6),
    description: '<p>316 不锈钢内胆，24 小时保温，杯口防漏设计。</p>'
  },
  {
    id: '13', name: '双肩背包 大容量通勤', price: '199.00', stock: 18, status: 0,
    categoryId: '22', categoryName: '鞋靴', image: IMG(1),
    description: '<p>可放 15.6 英寸笔记本，背部透气减压设计。</p>'
  }
]

/* ============================================================================
 * 二、可变的「数据库」：购物车 / 收藏 / 订单
 *  - 只存关联关系（cartId + productId + quantity），价格名称实时从 PRODUCTS 取
 *  - 初始数据故意覆盖各种边界：已下架商品、售罄商品、各状态订单
 * ========================================================================== */

const DB_KEY = '__mock_buyer_db__'

const ORDER_STATUS_TEXT = {
  10: '客户下单',
  20: '商家确认',
  30: '备货完成',
  40: '开始发货',
  50: '交易完成',
  90: '已取消'
}

function money(v) {
  return (Number(v) || 0).toFixed(2)
}

function buildOrder(no, status, items, createdAt) {
  const finalResult = status === 50 ? 'COMPLETED' : status === 90 ? 'CANCELLED' : null
  const rows = items.map(i => ({ ...i, subtotal: money(Number(i.price) * Number(i.quantity)) }))
  return {
    orderNo: no,
    status,
    statusText: ORDER_STATUS_TEXT[status],
    finalResult,
    totalAmount: money(rows.reduce((s, r) => s + Number(r.subtotal), 0)),
    itemCount: rows.reduce((s, r) => s + Number(r.quantity), 0),
    createdAt,
    items: rows
  }
}

function seedOrders() {
  return [
    // 10 / 20 / 30 → 会出现「取消订单」按钮
    buildOrder('20261115-0006', 10, [
      { productId: '4', name: '机械键盘 87 键 青轴', price: '279.00', quantity: 1 },
      { productId: '5', name: '无线鼠标 静音办公', price: '89.00', quantity: 2 }
    ], '2026-11-15T10:30:00+08:00'),
    buildOrder('20261114-0005', 20, [
      { productId: '3', name: '跑步鞋 男款 缓震回弹', price: '399.00', quantity: 1 }
    ], '2026-11-14T09:12:00+08:00'),
    buildOrder('20261113-0004', 30, [
      { productId: '1', name: '篮球 标准 7 号 室内外通用', price: '120.00', quantity: 3 }
    ], '2026-11-13T21:05:00+08:00'),
    // 40 开始发货 → 客户已不可取消，操作列显示「-」
    buildOrder('20261112-0003', 40, [
      { productId: '9', name: '智能手机 6.5 英寸 大电池', price: '2299.00', quantity: 1 }
    ], '2026-11-12T16:40:00+08:00'),
    // 终态：交易完成 / 已取消
    buildOrder('20261111-0002', 50, [
      { productId: '6', name: '蓝牙耳机 Pro 3 主动降噪', price: '899.00', quantity: 1 }
    ], '2026-11-11T11:20:00+08:00'),
    buildOrder('20261110-0001', 90, [
      { productId: '10', name: '不锈钢汤锅 24cm 带盖', price: '168.00', quantity: 1 }
    ], '2026-11-10T08:15:00+08:00'),
    // 以下几条只是为了撑出第二页（每页 10 条）
    buildOrder('20261109-0011', 50, [
      { productId: '12', name: '保温杯 500ml 长效保温', price: '79.00', quantity: 2 }
    ], '2026-11-09T19:30:00+08:00'),
    buildOrder('20261108-0010', 90, [
      { productId: '13', name: '双肩背包 大容量通勤', price: '199.00', quantity: 1 }
    ], '2026-11-08T14:02:00+08:00'),
    buildOrder('20261107-0009', 50, [
      { productId: '11', name: '纯棉四件套 1.8m 床', price: '259.00', quantity: 1 }
    ], '2026-11-07T10:45:00+08:00'),
    buildOrder('20261106-0008', 20, [
      { productId: '2', name: '足球 5 号 耐磨训练用球', price: '98.00', quantity: 2 }
    ], '2026-11-06T17:22:00+08:00'),
    buildOrder('20261105-0007', 90, [
      { productId: '5', name: '无线鼠标 静音办公', price: '89.00', quantity: 1 }
    ], '2026-11-05T09:08:00+08:00')
  ]
}

// 初始数据：购物车 3 条（含 1 条已下架）、收藏 12 条（含已下架，刚好跨两页）、订单 11 条
const SEED = {
  cart: [
    { cartId: 'c-1001', productId: '1', quantity: 2 },
    { cartId: 'c-1002', productId: '4', quantity: 1 },
    { cartId: 'c-1003', productId: '8', quantity: 1 } // 8 号已下架 → 不可勾选、不计入合计
  ],
  favorites: ['6', '8', '2', '5', '1', '3', '4', '7', '9', '10', '11', '12']
    .map((productId, i) => ({ productId, createdAt: `2026-11-${String(15 - i).padStart(2, '0')}T10:00:00+08:00` })),
  orders: seedOrders(),
  seq: 11 // 订单号自增序号
}

function loadDb() {
  try {
    const raw = localStorage.getItem(DB_KEY)
    if (raw) return JSON.parse(raw)
  } catch (e) {
    /* 解析失败就重建 */
  }
  const db = JSON.parse(JSON.stringify(SEED))
  saveDb(db)
  return db
}

function saveDb(db) {
  try {
    localStorage.setItem(DB_KEY, JSON.stringify(db))
  } catch (e) {
    /* 忽略配额异常 */
  }
}

/** 恢复初始 mock 数据（控制台执行 __mockReset() 即可调用） */
export function resetMockData() {
  localStorage.removeItem(DB_KEY)
  console.info('[MOCK] 数据已重置，正在刷新…')
  window.location.reload()
}

/* ============================================================================
 * 三、工具
 * ========================================================================== */

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

function findProduct(id) {
  return PRODUCTS.find(p => String(p.id) === String(id))
}

// 抛出和后端一致的业务错误结构（页面 catch 时读 e.code / e.message / e.data）
function bizError(code, extra) {
  throw {
    code,
    message: (extra && extra.message) || ERROR_MESSAGE[code] || '请求失败',
    data: extra && extra.data
  }
}

function isOnSale(product) {
  return !!product && Number(product.status) === 0
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function nextOrderNo(db) {
  const d = new Date()
  const ymd = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`
  db.seq = Number(db.seq || 0) + 1
  return `${ymd}-${String(db.seq).padStart(4, '0')}`
}

function paginate(list, page = 1, size = 10) {
  const p = Math.max(1, Number(page) || 1)
  const s = Math.max(1, Number(size) || 10)
  return { total: list.length, list: list.slice((p - 1) * s, p * s) }
}

/* ============================================================================
 * 四、对外的 mock 接口（函数名与 api/buyer.js 一一对应）
 *    每个函数返回 Promise，resolve 的值就是后端 code=0 时的 data
 * ========================================================================== */

async function wrap(fn) {
  if (MOCK_CONFIG.delay > 0) await sleep(MOCK_CONFIG.delay)
  return fn()
}

export const mockApi = {
  /* ---------------- 账号 ---------------- */
  // 任意用户名 + 任意密码都能登录；用户名填 fail 可以看到登录失败提示
  login(data = {}) {
    return wrap(() => {
      if (!data.username || !data.password) bizError(40001)
      if (String(data.username).toLowerCase() === 'fail') {
        bizError(40001, { message: '用户名或密码错误（这是 mock 的失败分支）' })
      }
      return { token: `mock-buyer-token-${Date.now()}`, username: data.username, nickname: data.username }
    })
  },

  register(data = {}) {
    return wrap(() => {
      if (!data.username) bizError(40001)
      return { id: String(Date.now()), username: data.username }
    })
  },

  /* ---------------- 商品 ---------------- */
  getCategories() {
    return wrap(() => CATEGORIES)
  },

  // 买家侧只返回在售商品（status === 0），支持关键词 + 二级分类 + 分页
  getProductList(params = {}) {
    return wrap(() => {
      const keyword = String(params.keyword || '').trim().toLowerCase()
      const categoryId = String(params.categoryId || '').trim()

      let list = PRODUCTS.filter(p => Number(p.status) === 0)
      if (categoryId) list = list.filter(p => String(p.categoryId) === categoryId)
      if (keyword) {
        list = list.filter(p =>
          p.name.toLowerCase().includes(keyword) ||
          String(p.description || '').toLowerCase().includes(keyword)
        )
      }

      const { total, list: rows } = paginate(list, params.page, params.pageSize)
      return {
        total,
        page: Number(params.page) || 1,
        pageSize: Number(params.pageSize) || 10,
        // 列表页用 mainImage，顺手补上 imageUrl，字段名兼容后端可能的差异
        list: rows.map(p => ({ ...p, mainImage: p.image, imageUrl: p.image }))
      }
    })
  },

  getProductDetail(id) {
    return wrap(() => {
      const p = findProduct(id)
      if (!p) bizError(40401, { message: '商品不存在或已删除' })
      return { ...p, images: [p.image], imageUrl: p.image }
    })
  },

  /* ---------------- 收藏（B.2-1） ---------------- */
  addFavorite(productId) {
    return wrap(() => {
      const db = loadDb()
      const p = findProduct(productId)
      if (!p) bizError(40934)

      // 已收藏再调：返回成功且不产生第二条
      if (db.favorites.some(f => String(f.productId) === String(productId))) return { duplicated: true }
      if (db.favorites.length >= 200) bizError(40931)

      db.favorites.unshift({ productId: String(productId), createdAt: new Date().toISOString() })
      saveDb(db)
      return { added: true }
    })
  },

  removeFavorite(productId) {
    return wrap(() => {
      const db = loadDb()
      db.favorites = db.favorites.filter(f => String(f.productId) !== String(productId))
      saveDb(db)
      return { removed: true }
    })
  },

  getFavoriteList(params = {}) {
    return wrap(() => {
      if (MOCK_CONFIG.emptyFavorites) return { total: 0, list: [] }

      const db = loadDb()
      const rows = db.favorites.map(f => {
        const p = findProduct(f.productId)
        return {
          productId: String(f.productId),
          name: p ? p.name : '商品已删除',
          price: p ? p.price : '0.00',
          image: p ? p.image : '',
          available: isOnSale(p) // 已下架 → 置灰
        }
      })
      const { total, list } = paginate(rows, params.page, params.size)
      return { total, list }
    })
  },

  /* ---------------- 购物车（B.2-2） ---------------- */
  addToCart(productId, quantity = 1) {
    return wrap(() => {
      const db = loadDb()
      const p = findProduct(productId)
      const qty = Number(quantity) || 1

      if (!p) bizError(40934)
      if (!isOnSale(p)) bizError(40926)

      const exist = db.cart.find(l => String(l.productId) === String(productId))
      if (exist) {
        if (exist.quantity + qty > 99) bizError(40932)
        exist.quantity += qty
      } else {
        if (db.cart.length >= 50) bizError(40931)
        db.cart.push({ cartId: `c-${Date.now()}`, productId: String(productId), quantity: qty })
      }

      saveDb(db)
      return { ok: true }
    })
  },

  getCartList() {
    return wrap(() => {
      if (MOCK_CONFIG.emptyCart) return { total: 0, totalAmount: '0.00', list: [] }

      const db = loadDb()
      const list = db.cart.map(l => {
        const p = findProduct(l.productId)
        const price = p ? p.price : '0.00'
        const available = isOnSale(p)
        return {
          cartId: String(l.cartId),
          productId: String(l.productId),
          name: p ? p.name : '商品已删除',
          price,
          quantity: Number(l.quantity),
          subtotal: money(Number(price) * Number(l.quantity)),
          image: p ? p.image : '',
          available
        }
      })
      // 合计只统计在售商品（和页面口径一致）
      const totalAmount = money(
        list.filter(i => i.available).reduce((s, i) => s + Number(i.subtotal), 0)
      )
      return { total: list.length, totalAmount, list }
    })
  },

  updateCartQuantity(cartId, quantity) {
    return wrap(() => {
      const db = loadDb()
      const qty = Number(quantity)
      if (!qty || qty < 1) bizError(40001)
      if (qty > 99) bizError(40932)

      const line = db.cart.find(l => String(l.cartId) === String(cartId))
      if (!line) bizError(40935)

      line.quantity = qty
      saveDb(db)
      return { ok: true }
    })
  },

  removeCartItem(cartId) {
    return wrap(() => {
      const db = loadDb()
      db.cart = db.cart.filter(l => String(l.cartId) !== String(cartId))
      saveDb(db)
      return { ok: true }
    })
  },

  // 结算下单：校验已下架 → 校验库存 → 建单 → 清空已结算条目
  checkoutCart(cartIds = []) {
    return wrap(() => {
      const db = loadDb()
      const ids = cartIds.map(String)
      if (ids.length === 0) bizError(40933)

      const lines = db.cart.filter(l => ids.includes(String(l.cartId)))
      if (lines.length === 0) bizError(40933)

      // 1) 已下架商品不能进交易
      const hasOffShelf = lines.some(l => !isOnSale(findProduct(l.productId)))
      if (hasOffShelf) bizError(40926)

      // 2) 库存校验：40924 必须带上 shortage，页面靠它标红
      const shortage = []
      lines.forEach(l => {
        const p = findProduct(l.productId)
        if (MOCK_CONFIG.simulateShortage || Number(l.quantity) > Number(p.stock)) {
          shortage.push({ productId: String(l.productId), name: p.name, stock: Number(p.stock) })
        }
      })
      if (shortage.length > 0) {
        bizError(40924, { message: '部分商品库存不足', data: { shortage } })
      }

      // 3) 建单
      const items = lines.map(l => {
        const p = findProduct(l.productId)
        return { productId: String(l.productId), name: p.name, price: p.price, quantity: Number(l.quantity) }
      })
      const order = buildOrder(nextOrderNo(db), 10, items, new Date().toISOString())
      db.orders.unshift(order)

      // 4) 已结算的购物车条目出车
      db.cart = db.cart.filter(l => !ids.includes(String(l.cartId)))
      saveDb(db)

      return { orderNo: order.orderNo, totalAmount: order.totalAmount }
    })
  },

  // 购物车多件转收藏（B.2-4）
  cartToFavorite(cartIds = []) {
    return wrap(() => {
      const db = loadDb()
      const ids = cartIds.map(String)
      const lines = db.cart.filter(l => ids.includes(String(l.cartId)))
      if (lines.length === 0) bizError(40933)

      let converted = 0
      lines.forEach(l => {
        const exists = db.favorites.some(f => String(f.productId) === String(l.productId))
        if (exists) return
        if (db.favorites.length >= 200) return // 超上限的跳过，最后按实际转换数返回
        db.favorites.unshift({ productId: String(l.productId), createdAt: new Date().toISOString() })
        converted += 1
      })

      db.cart = db.cart.filter(l => !ids.includes(String(l.cartId)))
      saveDb(db)
      return { converted }
    })
  },

  /* ---------------- 订单（B.1） ---------------- */
  getMyOrderList(params = {}) {
    return wrap(() => {
      if (MOCK_CONFIG.emptyOrders) return { total: 0, page: 1, size: 10, list: [] }

      const db = loadDb()
      const sorted = [...db.orders].sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      )
      const { total, list } = paginate(sorted, params.page, params.size)
      return { total, page: Number(params.page) || 1, size: Number(params.size) || 10, list }
    })
  },

  getOrderDetail(orderNo) {
    return wrap(() => {
      const db = loadDb()
      const order = db.orders.find(o => String(o.orderNo) === String(orderNo))
      if (!order) bizError(40927)
      return order
    })
  },

  // 客户只能在 10 / 20 / 30 取消；40 报 40921，终态报 40923
  cancelOrder(orderNo) {
    return wrap(() => {
      const db = loadDb()
      const order = db.orders.find(o => String(o.orderNo) === String(orderNo))
      if (!order) bizError(40927)

      const status = Number(order.status)
      if (status === 50 || status === 90) bizError(40923)
      if (![10, 20, 30].includes(status)) bizError(40921)

      order.status = 90
      order.statusText = ORDER_STATUS_TEXT[90]
      order.finalResult = 'CANCELLED'
      saveDb(db)
      return { ok: true }
    })
  }
}

/* ============================================================================
 * 五、Mock 开着时的控制台提示（关掉后这段不会执行）
 * ========================================================================== */
if (MOCK_ENABLED) {
  console.info(
    '%c[MOCK] 买家端已启用 Mock 数据，接口不会请求后端',
    'color:#fff;background:#e60012;padding:2px 6px;border-radius:3px'
  )
  console.info('[MOCK] 恢复初始数据：__mockReset()　｜　关闭 Mock：改 MOCK_ENABLED = false')
  window.__mockReset = resetMockData
}
