# 挑战需求 B · 接口契约（订单域 + 收藏 / 购物车 / 结算）

> **定位：需求侧定稿，实现方按此实现。**
> 按分工表，需求 B 的用户故事、状态流转图、接口契约由产品经理（杜美芳）统一出具；订单域实现方为杜美芳，收藏与购物车实现方为李炫苇。
> 契约与实现不一致时，先对齐契约再改代码，改动走禅道需求变更留痕，避免各自定义导致前端返工。
> 前端对接：童昕祺（买家端）、陈泽英（卖家端）。

---

## 一、通用约定（沿用迭代 2）

| 项 | 约定 |
|---|---|
| 基础路径 | `/api` |
| 鉴权 | `Authorization: Bearer <token>`；买家接口需登录态，卖家接口需卖家 token |
| 响应结构 | `{"code": 0, "message": "ok", "data": ...}`，`code != 0` 为业务失败 |
| 金额 | **字符串**，两位小数，如 `"256.00"` |
| ID | **字符串**，避免前端精度丢失（`productId`、`cartId`、`customerId` 均如此） |
| 时间 | ISO 8601 带时区，如 `2026-11-12T10:30:00+08:00` |
| 错误码分段 | 40901~40917 迭代 2；**40921~40927 订单域**；**40931~40935 购物车 / 收藏** |
| 幂等 | 状态流转**不做**幂等静默成功，重复提交直接报 40921；收藏重复添加静默成功 |

---

## 二、接口总览

**订单域（9 个，实现方杜美芳）**

| # | 方法 | 路径 | 说明 | 调用方 |
|---|---|---|---|---|
| 1 | POST | `/api/order` | 创建订单（下单） | 结算接口内部调用 |
| 2 | GET | `/api/order/my` | 我的订单列表（含已取消） | 买家端 |
| 3 | GET | `/api/order/{orderNo}` | 订单详情（校验归属） | 买家端 / 卖家端 |
| 4 | GET | `/api/seller/customer/{customerId}/order` | 某客户的订单记录（含最终结果） | 卖家端 |
| 5 | POST | `/api/order/{orderNo}/confirm` | 商家确认（10→20） | 卖家端 |
| 6 | POST | `/api/order/{orderNo}/ready` | 备货完成（20→30） | 卖家端 |
| 7 | POST | `/api/order/{orderNo}/ship` | 开始发货（30→40） | 卖家端 |
| 8 | POST | `/api/order/{orderNo}/complete` | 交易完成（40→50） | 卖家端 |
| 9 | POST | `/api/order/{orderNo}/cancel` | 取消订单（角色由 token 判定） | 买家端 / 卖家端 |

**收藏 / 购物车 / 结算（9 个，实现方李炫苇）**

| # | 方法 | 路径 | 说明 |
|---|---|---|---|
| 10 | POST | `/api/favorite` | 添加收藏 |
| 11 | DELETE | `/api/favorite/{productId}` | 取消收藏 |
| 12 | GET | `/api/favorite?page=1&size=10` | 收藏列表 |
| 13 | POST | `/api/cart` | 加入购物车 |
| 14 | PUT | `/api/cart/{cartId}` | 修改数量 |
| 15 | DELETE | `/api/cart/{cartId}` | 移除单条 |
| 16 | GET | `/api/cart` | 购物车列表 |
| 17 | POST | `/api/cart/checkout` | 结算下单（内部调 1） |
| 18 | POST | `/api/cart/to-favorite` | 勾选多件转收藏 |

---

## 三、订单域接口

### 1. 创建订单 `POST /api/order`

请求：
```json
{
  "items": [
    { "productId": "1001", "quantity": 2 },
    { "productId": "1002", "quantity": 1 }
  ]
}
```

成功：
```json
{
  "code": 0,
  "data": {
    "orderNo": "20261112-0001",
    "status": 10,
    "totalAmount": "256.00",
    "createdAt": "2026-11-12T10:30:00+08:00",
    "items": [
      { "productId": "1001", "name": "机械键盘", "price": "99.00", "quantity": 2, "subtotal": "198.00" }
    ]
  }
}
```

规则：
- 逐条校验库存，任一不足 → 整单不生成，返回 40924 + 缺货清单
- 成功后**在同一事务内**扣减库存；扣到 0 的商品自动置「已下架」
- 单价取自商品当前价（商品发布后不可改价）
- `items` 上限 20 条
- 前端不直接调用本接口，走 `/api/cart/checkout`

失败：
```json
{
  "code": 40924,
  "message": "部分商品库存不足",
  "data": { "shortage": [ { "productId": "1002", "name": "鼠标", "required": 1, "available": 0 } ] }
}
```

### 2. 取消订单 `POST /api/order/{orderNo}/cancel`

- 请求体为空；角色由 token 判定，`seller` 走商家规则，`customer` 走客户规则
- 客户：状态 10/20/30 可取消；状态 40 报 40921
- 商家：状态 10/20/30/40 可取消；状态 50 报 40923
- 成功后状态置 90，**同事务内按明细回补库存**；回补后库存 > 0 且商品因归零下架的，**自动恢复在售**

### 3. 状态流转 `confirm / ready / ship / complete`

- 请求体为空，仅校验：具备卖家身份、订单当前状态等于该动作的前置状态
- 返回 `{ "code": 0, "data": { "orderNo": "...", "status": 20 } }`
- 顺序校验失败一律 40921，响应带 `currentStatus` 与 `expectedStatus`

### 4. 列表查询

`GET /api/order/my?page=1&size=10`、`GET /api/seller/customer/{customerId}/order?page=1&size=10`

```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "orderNo": "20261112-0001",
        "status": 10,
        "statusText": "客户下单",
        "finalResult": null,
        "totalAmount": "256.00",
        "itemCount": 2,
        "createdAt": "2026-11-12T10:30:00+08:00",
        "items": [
          { "productId": "1001", "name": "机械键盘", "price": "99.00", "quantity": 2, "subtotal": "198.00" }
        ]
      }
    ],
    "total": 1, "page": 1, "size": 10
  }
}
```

- 买家列表默认含已取消，排序按下单时间倒序
- `finalResult`：终态时为 `COMPLETED`（交易完成）/ `CANCELLED`（已取消），非终态为 `null`
- 卖家不提供全局订单列表、不按状态筛选，只能按客户查

---

## 四、收藏 / 购物车 / 结算接口

### 涉及表（实现方定稿）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `favorite` | `id`、`customer_id`、`product_id`、`created_at` | 客户 + 商品唯一 |
| `cart` | `id`、`customer_id`、`product_id`、`quantity`、`created_at`、`updated_at` | 客户 + 商品唯一 |

- 上限：收藏 200 条、购物车 50 条、单条数量 99
- 两表**不锁库存、不占位**，加购与收藏不校验库存
- 下单后由结算接口删除已下单的购物车项；订单域不碰 `cart` 表

### 5. 添加收藏 `POST /api/favorite`

请求 `{ "productId": "12" }`，返回 `{ "code": 0, "data": { "favoriteId": "8" } }`

- 已收藏再调：返回 0，不报错、不产生第二条
- 商品不存在 40934；已下架商品允许收藏（列表标「已下架」）
- 收藏达 200 → 40931

### 6. 收藏列表 `GET /api/favorite?page=1&size=10`

```json
{
  "code": 0,
  "data": {
    "total": 12,
    "list": [
      { "productId": "12", "name": "篮球", "price": "120.00", "available": true, "favoritedAt": "2026-11-10T14:02:11+08:00" },
      { "productId": "7", "name": "球鞋", "price": "399.00", "available": false, "favoritedAt": "2026-11-09T10:20:00+08:00" }
    ]
  }
}
```

- `available=false` 置灰标「已下架」
- 收藏列表**不提供**「加入购物车」「直接下单」，只能点进商品详情

### 7. 加入购物车 `POST /api/cart`

请求 `{ "productId": "12", "quantity": 1 }`，返回 `{ "code": 0, "data": { "cartId": "33", "quantity": 3 } }`

- 同商品已在车内：**累加**数量，累加后超 99 → 40932
- 已有 50 条且是新商品 → 40931
- 不做「清空购物车」「全选」，需求未要求

### 8. 修改数量 `PUT /api/cart/{cartId}`

请求 `{ "quantity": 2 }`；超 99 → 40932，低于 1 → 40001，非本人购物车项 → 40935

### 9. 购物车列表 `GET /api/cart`

```json
{
  "code": 0,
  "data": {
    "total": 3,
    "totalAmount": "918.00",
    "list": [
      { "cartId": "33", "productId": "12", "name": "篮球", "price": "120.00", "quantity": 2, "subtotal": "240.00", "available": true },
      { "cartId": "34", "productId": "7", "name": "球鞋", "price": "399.00", "quantity": 1, "subtotal": "399.00", "available": false }
    ]
  }
}
```

- 下架商品保留并给 `available=false`，结算时拦截
- `totalAmount` 只累加 `available=true` 的行

### 10. 结算下单 `POST /api/cart/checkout`

请求 `{ "cartIds": ["33", "34"] }`，返回 `{ "code": 0, "data": { "orderNo": "20261112-0001", "totalAmount": "918.00" } }`

内部流程：校验非空 → 读购物车项 → 组装 `items[]`（只含 `productId` + `quantity`）→ 同进程调 `OrderService.create` → 成功则删除已下单购物车项并返回 `orderNo`

### 11. 多件转收藏 `POST /api/cart/to-favorite`

请求 `{ "cartIds": ["33", "34"] }`，返回 `{ "code": 0, "data": { "converted": 2 } }`

- 受收藏上限 200 约束，超出 → 40931
- 已在收藏中的静默跳过，不报错
- 转出成功即从购物车移除对应项

---

## 五、两域交接面（结算 → 下单，单向）

```
买家端 → POST /api/cart/checkout（李炫苇实现）
              ↓ 同进程服务调用
         OrderService.create(customerId, items[])（杜美芳实现）
              ↓
         order + order_item 落库、扣库存
```

约定：
1. **`order` 与 `order_item` 表只由订单域写入**；结算不得直接 insert / update
2. 结算负责：校验购物车非空、读购物车项、组装 `items[]`、按上限校验（50 条 / 单条 99）
3. 订单域负责：库存校验与扣减、单价定格、金额计算、订单号生成、扣到 0 时下架商品
4. **库存扣减只发生在订单域**，结算不得重复扣减
5. 下单成功后移除哪些购物车项由结算负责（订单域不操作购物车表）
6. 采用同进程 `@Service` 调用，不走 HTTP 自调用，避免鉴权与超时叠加
7. 若确需 HTTP 暴露，`POST /api/order` 加内部调用标记，**前端不直接调用**

> 交接面只有 `items[]` 一个数据结构，字段为 `productId` + `quantity`，不含价格。

---

## 六、错误码总表

| 码 | 含义 | 归属 |
|---|---|---|
| 40921 | 订单状态不允许该操作（跳态 / 回退 / 重复提交 / 客户发货后取消） | 订单域 |
| 40922 | 无权操作该订单（非归属客户、非卖家） | 订单域 |
| 40923 | 订单已是终态 | 订单域 |
| 40924 | 库存不足 | 订单域（结算透传） |
| 40925 | 提交内容为空 | 订单域 |
| 40926 | 商品已下架 | 订单域 |
| 40927 | 订单不存在 | 订单域 |
| 40931 | 收藏或购物车条数已达上限 | 购物车 / 收藏 |
| 40932 | 单条数量超上限（99） | 购物车 |
| 40933 | 购物车为空 / 未选中任何商品 | 购物车 |
| 40934 | 商品不存在 | 购物车 / 收藏 |
| 40935 | 购物车项不属于当前用户 | 购物车 |

结算时订单域错误码原样透传，并带 `data.shortage`：

```json
{ "code": 40924, "message": "部分商品库存不足", "data": { "shortage": [ { "productId": "7", "name": "球鞋", "required": 2, "stock": 1 } ] } }
```

---

## 七、前端对接要点

**童昕祺（买家端）**
- 下单按钮不要直接调 `/api/order`，调 `/api/cart/checkout`
- 40924 用 `data.shortage` 在购物车标红对应商品，提示剔除后重下
- 订单列表用 `/api/order/my`，`statusText` 直接展示，不要自己映射码值
- 取消按钮：状态 10/20/30 显示，40 及以后隐藏
- 加购 / 收藏需登录，未登录先跳登录，登录后恢复临时加购
- 下架商品在购物车标「已下架」并禁用勾选

**陈泽英（卖家端）**
- 从客户详情页进入该客户订单记录，在该页推进或取消；不做全局订单列表，不按状态筛选
- 推进按钮可用条件：`confirm` 仅 10、`ready` 仅 20、`ship` 仅 30、`complete` 仅 40
- 取消按钮在 10/20/30/40 显示，50 之后隐藏
- 40921 / 40923 提示后刷新列表（状态已被变更）

---

## 八、未覆盖项

| 项 | 口径 |
|---|---|
| 订单详情独立页面路由 | 需求未定义，组内定为列表展开，不提供独立页 |
| 各状态时间戳 | 需求未定义，只提供 `createdAt` |
| 取消原因 / 取消时间 | 需求明确不记录 |
| 订单修改（数量 / 部分取消 / 地址） | 需求明确不支持 |
| 购物车清空 / 全选 | 需求未要求，不做 |
| 购物车 / 收藏分页 | 购物车 50 条一次返回不分页；收藏 200 条按 10 条分页 |
