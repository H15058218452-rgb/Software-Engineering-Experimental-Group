# 挑战需求 C · 接口规范（支付 / 地址 / 物流 / 售后）

> **定位：需求侧定稿，实现方按此实现。**
> 与迭代 3 的规范冲突时，以本文件为准；改动走禅道需求变更留痕。
> 前端对接：买家端（童昕祺）、卖家端（陈泽英）。

---

## 一、通用约定（沿用迭代 3）

| 项 | 约定 |
|---|---|
| 基础路径 | `/api` |
| 鉴权 | `Authorization: Bearer <token>`；买家接口需登录态，卖家接口需卖家 token |
| 响应结构 | `{"code": 0, "message": "ok", "data": ...}`，`code != 0` 为业务失败 |
| 金额 | **字符串**，两位小数，如 `"256.00"` |
| ID | **字符串**，避免前端精度丢失 |
| 时间 | ISO 8601 带时区，如 `2026-11-12T10:30:00+08:00` |
| 幂等 | 状态流转不静默成功，重复提交直接报 40921；支付回调按 `paymentNo` 幂等 |
| 错误码分段 | 40921~40927 订单域；**40941~40945 支付**；**40951~40954 地址**；**40961~40968 售后**；**40971~40972 物流** |

---

## 二、接口总览

**订单域（8 个，含 3 处变更）**

| # | 方法 | 路径 | 说明 | 变更 |
|---|---|---|---|---|
| 1 | POST | `/api/order` | 创建订单（下单） | 新增 `addressId`，成功后状态 10→15 |
| 2 | POST | `/api/order/{orderNo}/pay` | 发起支付 | 新增 |
| 3 | GET | `/api/order/{orderNo}/pay` | 查询支付结果 | 新增 |
| 4 | POST | `/api/order/{orderNo}/confirm` | 商家确认（18→20） | 前置状态改为 18 |
| 5 | POST | `/api/order/{orderNo}/ready` | 备货完成（20→30） | 前置状态改为 20 |
| 6 | POST | `/api/order/{orderNo}/ship` | 发货（30→40） | 新增必填 `logisticsNo`、`carrier` |
| 7 | POST | `/api/order/{orderNo}/receive` | **客户确认收货**（40→50） | 替代原 `complete`，仅买家可调 |
| 8 | POST | `/api/order/{orderNo}/cancel` | 取消订单 | 规则按角色与状态区分，已支付须退款 |

**支付域（内部 1 个 + 对外 2 个，见上表 #2 #3）**

| # | 方法 | 路径 | 说明 |
|---|---|---|---|
| 9 | POST | `/api/pay/callback` | 支付渠道异步回调（内部接口，前端不调） |

**地址域（5 个）**

| # | 方法 | 路径 | 说明 |
|---|---|---|---|
| 10 | GET | `/api/address` | 我的地址列表 |
| 11 | POST | `/api/address` | 新增地址 |
| 12 | PUT | `/api/address/{addressId}` | 修改地址 |
| 13 | DELETE | `/api/address/{addressId}` | 删除地址 |
| 14 | PUT | `/api/address/{addressId}/default` | 设为默认地址 |

**物流（1 个）**

| # | 方法 | 路径 | 说明 |
|---|---|---|---|
| 15 | GET | `/api/order/{orderNo}/logistics` | 查询物流单号与轨迹 |

**售后域（9 个）**

| # | 方法 | 路径 | 说明 | 调用方 |
|---|---|---|---|---|
| 16 | POST | `/api/after-sale` | 发起售后申请 | 买家 |
| 17 | GET | `/api/after-sale/my` | 我的售后单列表 | 买家 |
| 18 | GET | `/api/after-sale/{afterSaleNo}` | 售后单详情 | 买家 / 卖家 |
| 19 | POST | `/api/after-sale/{afterSaleNo}/cancel` | 客户撤销售后 | 买家 |
| 20 | GET | `/api/seller/after-sale` | 全部售后单列表 | 卖家 |
| 21 | POST | `/api/seller/after-sale/{afterSaleNo}/agree-return` | 同意退货（待处理→待收货） | 卖家 |
| 22 | POST | `/api/seller/after-sale/{afterSaleNo}/receive` | 确认收到退货并退款 | 卖家 |
| 23 | POST | `/api/seller/after-sale/{afterSaleNo}/refund-only` | 仅退款 | 卖家 |
| 24 | POST | `/api/seller/after-sale/{afterSaleNo}/reject` | 拒绝售后 | 卖家 |

---

## 三、订单域

### 1. 创建订单 `POST /api/order`

请求：
```json
{
  "addressId": "12",
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
    "orderNo": "20261123-0001",
    "status": 15,
    "statusText": "待支付",
    "totalAmount": "256.00",
    "expireAt": "2026-11-24T10:30:00+08:00",
    "createdAt": "2026-11-23T10:30:00+08:00"
  }
}
```

规则：
- `addressId` 必填；地址必须属于当前客户（否则 40953）；无地址时前端先引导新增，后端报 40954
- 下单时把收货人、手机号、详细地址写入订单快照
- 逐条校验并扣减库存，任一不足整单不生成，返回 40924 + 缺货清单
- 扣到 0 的商品自动置「已下架」
- `items` 上限 20 条
- 订单创建后状态先置 10，同事务内立即置 15，对外只暴露 15
- `expireAt` = 创建时间 + 24 小时，前端据此展示倒计时

### 2. 发起支付 `POST /api/order/{orderNo}/pay`

请求：
```json
{ "channel": "ALIPAY" }
```

渠道枚举：`BANK_CARD`（银行卡）、`CREDIT_CARD`（信用卡）、`ALIPAY`（支付宝）、`WECHAT`（微信）。一次只能选一个，不支持组合支付。

成功：
```json
{
  "code": 0,
  "data": {
    "paymentNo": "PAY202611230001",
    "orderNo": "20261123-0001",
    "channel": "ALIPAY",
    "amount": "256.00",
    "payUrl": "https://sandbox.example.com/pay?token=xxx",
    "expireAt": "2026-11-24T10:30:00+08:00"
  }
}
```

规则：
- 仅状态 15 可发起，其他状态报 40941
- 同一订单重复发起：已存在未完成的支付单则直接返回原 `paymentNo`，不新建（防重复支付）
- 金额由服务端按订单金额生成，前端传入一律忽略
- `payUrl` 只是支付入口，本期可接沙箱或 Mock，接口形状不变

### 3. 查询支付结果 `GET /api/order/{orderNo}/pay`

```json
{
  "code": 0,
  "data": {
    "orderNo": "20261123-0001",
    "orderStatus": 18,
    "statusText": "已支付",
    "paymentNo": "PAY202611230001",
    "channel": "ALIPAY",
    "amount": "256.00",
    "paidAt": "2026-11-23T10:32:11+08:00"
  }
}
```

- 前端在支付返回页轮询本接口，成功后刷新订单
- 未支付时 `orderStatus` 为 15、`paidAt` 为 `null`

### 4. 支付回调 `POST /api/pay/callback`（内部）

```json
{ "paymentNo": "PAY202611230001", "channel": "ALIPAY", "amount": "256.00", "tradeNo": "ALI2026xxxx", "paidAt": "2026-11-23T10:32:11+08:00" }
```

规则：
- 按 `paymentNo` 幂等：已成功则直接返回成功，不重复改状态
- 校验回调金额与支付单金额一致，不一致置支付单为异常并告警，订单保持 15
- 成功则同事务内：支付单置成功 → 订单 15→18 → 记录渠道、支付时间，**不动库存**（下单已扣）
- 前端不可调用，只允许渠道侧或内部任务调用

### 5. 状态推进 `confirm / ready / ship`

| 接口 | 前置 | 后续 | 调用方 |
|---|---|---|---|
| `confirm` | 18 已支付 | 20 商家确认 | 卖家 |
| `ready` | 20 商家确认 | 30 备货完成 | 卖家 |
| `ship` | 30 备货完成 | 40 开始发货 | 卖家 |

`ship` 请求：
```json
{ "logisticsNo": "SF1234567890", "carrier": "顺丰速运" }
```

- `logisticsNo`、`carrier` 均为必填，空则 40972
- 发货成功同事务写入 `logistics` 表，状态置 `SHIPPED`
- 前置状态不符一律 40921，响应带 `currentStatus` 与 `expectedStatus`

### 6. 客户确认收货 `POST /api/order/{orderNo}/receive`

- 仅买家 token 可调，卖家调用报 40922
- 前置状态必须为 40，否则 40921
- 成功：40→50，记录 `completedAt`，不动库存
- 系统不做超时自动确认，不存在定时任务把 40 推为 50

### 7. 取消订单 `POST /api/order/{orderNo}/cancel`

请求：
```json
{ "reason": "不想要了" }
```

`reason` 为选填字符串，最长 100 字。

| 角色 | 可取消状态 | 退款 | 库存 |
|---|---|---|---|
| 客户 | 15 | 否 | 回补 |
| 客户 | 18 / 20 / 30 | 是，原路 | 回补 |
| 客户 | 40 / 50 | 不允许（40921） | — |
| 商家 | 15 | 否 | 回补 |
| 商家 | 18 / 20 / 30 | 是，原路 | 回补 |
| 商家 | 40 | 是，原路 | **不回补**（货已发出） |
| 商家 | 50 | 不允许（40923） | — |

- 已支付订单取消时，同事务内调用退款逻辑，生成退款记录
- 退款渠道取原支付单的 `channel`，严格原路

---

## 四、地址域

涉及表 `address`，字段见第八节。单客户最多 10 条。

### 8. 地址列表 `GET /api/address`

```json
{
  "code": 0,
  "data": {
    "total": 2,
    "list": [
      { "addressId": "12", "receiver": "张三", "phone": "13800138000", "region": "浙江省杭州市西湖区", "detail": "文一西路 100 号 3 幢 502", "isDefault": true },
      { "addressId": "13", "receiver": "张三", "phone": "13900139000", "region": "浙江省杭州市余杭区", "detail": "某某路 8 号", "isDefault": false }
    ]
  }
}
```

- 默认地址排在最前，其余按创建时间倒序

### 9. 新增地址 `POST /api/address`

```json
{ "receiver": "张三", "phone": "13800138000", "region": "浙江省杭州市西湖区", "detail": "文一西路 100 号 3 幢 502", "isDefault": true }
```

- 四个字段均必填，空报 40925
- 手机号须为 11 位数字，否则 40925
- 已有 10 条再新增报 40952
- `isDefault=true` 时，把该客户其他地址的默认标记清掉
- 客户的第一条地址自动设为默认

### 10. 修改地址 `PUT /api/address/{addressId}`

请求字段同新增，全部可选传，传了才覆盖。

- 非本人地址 40953，不存在 40951

### 11. 删除地址 `DELETE /api/address/{addressId}`

- 删除的是默认地址时，自动把该客户**最近创建**的一条设为默认
- 删完没有地址则不设默认
- 已被订单引用的地址可以删除，不影响订单快照

### 12. 设为默认 `PUT /api/address/{addressId}/default`

- 返回 `{ "code": 0, "data": { "addressId": "12", "isDefault": true } }`
- 同客户其他地址默认标记自动清除

---

## 五、物流

### 13. 查询物流 `GET /api/order/{orderNo}/logistics`

```json
{
  "code": 0,
  "data": {
    "orderNo": "20261123-0001",
    "logisticsNo": "SF1234567890",
    "carrier": "顺丰速运",
    "status": "IN_TRANSIT",
    "statusText": "运输中",
    "traces": [
      { "time": "2026-11-24T09:12:00+08:00", "desc": "快件已到达杭州转运中心" },
      { "time": "2026-11-23T18:40:00+08:00", "desc": "顺丰速运已收取快件" }
    ]
  }
}
```

- 状态枚举：`SHIPPED` 已发货、`IN_TRANSIT` 运输中、`SIGNED` 已签收
- 订单未发货（状态 < 40）报 40971
- 一个订单只有一条物流记录，不拆包裹
- 不做异常预警、不做主动推送；`traces` 可为空数组
- 签收前商家可改单号（复用 `ship` 的修改接口：`PUT /api/order/{orderNo}/logistics`，请求同 `ship` 字段，仅状态 40 且未签收可改）

---

## 六、售后域

售后单状态：`PENDING` 待处理、`WAIT_RETURN` 待收货、`REFUNDED` 已退款、`REJECTED` 已拒绝、`CANCELLED` 已撤销。

类型：`RETURN_REFUND` 退货退款、`REFUND_ONLY` 仅退款。

### 14. 发起售后 `POST /api/after-sale`

```json
{
  "orderNo": "20261123-0001",
  "productId": "1001",
  "quantity": 1,
  "title": "键盘按键失灵",
  "description": "收到后 W 键无反应，已录视频",
  "images": ["https://cdn.example.com/a.jpg"]
}
```

规则：
- 订单状态必须是 40 或 50，否则 40962；订单归属校验失败 40922
- `quantity` 不得超过该商品在该订单中的购买数量，否则 40963
- `title` 必填，最长 50 字；`description` 必填，最长 500 字；`images` 选填，最多 5 张，单张 ≤ 2MB，仅 jpg / png / webp，超限 40968
- 同一商品同时只能有一个进行中的售后单（PENDING / WAIT_RETURN），否则 40964
- 同一商品累计售后不超过 3 次（含已拒绝、已撤销、已退款），否则 40965
- 成功后售后单 `PENDING`，**订单状态不变**

返回：
```json
{ "code": 0, "data": { "afterSaleNo": "AS202611230001", "status": "PENDING" } }
```

### 15. 我的售后单 `GET /api/after-sale/my?page=1&size=10`

```json
{
  "code": 0,
  "data": {
    "total": 1,
    "list": [
      { "afterSaleNo": "AS202611230001", "orderNo": "20261123-0001", "productId": "1001", "productName": "机械键盘", "quantity": 1, "status": "PENDING", "statusText": "待处理", "createdAt": "2026-11-25T14:00:00+08:00" }
    ]
  }
}
```

- 只能看到自己的售后单

### 16. 售后单详情 `GET /api/after-sale/{afterSaleNo}`

```json
{
  "code": 0,
  "data": {
    "afterSaleNo": "AS202611230001",
    "orderNo": "20261123-0001",
    "productId": "1001",
    "productName": "机械键盘",
    "quantity": 1,
    "title": "键盘按键失灵",
    "description": "收到后 W 键无反应",
    "images": ["https://cdn.example.com/a.jpg"],
    "type": null,
    "status": "PENDING",
    "statusText": "待处理",
    "refundAmount": null,
    "refundChannel": null,
    "refundedAt": null,
    "createdAt": "2026-11-25T14:00:00+08:00"
  }
}
```

- 买家只能看自己的（40967），卖家可看全部

### 17. 客户撤销售后 `POST /api/after-sale/{afterSaleNo}/cancel`

- 仅 `PENDING` 可撤销，其他状态 40966
- 撤销后转 `CANCELLED`，不退款、不回补库存、订单状态不变
- 撤销计入该商品累计次数

### 18. 商家售后列表 `GET /api/seller/after-sale?status=PENDING&page=1&size=10`

- 单卖家模式，返回全部售后单
- `status` 选填，取值同状态枚举

### 19. 同意退货 `POST /api/seller/after-sale/{afterSaleNo}/agree-return`

- 仅 `PENDING` 可操作，否则 40966
- 成功转 `WAIT_RETURN`，`type` 置 `RETURN_REFUND`
- 此时**不退款、不回补库存**

### 20. 确认收到退货并退款 `POST /api/seller/after-sale/{afterSaleNo}/receive`

请求：
```json
{ "refundNote": "已收到退回件" }
```

规则：
- 仅 `WAIT_RETURN` 可操作，否则 40966
- 同事务内：按 `quantity` 回补库存（每件 +1）→ 按 `快照单价 × quantity` 原路退款 → 售后单转 `REFUNDED`
- 退款金额由服务端计算，前端不传
- 回补后库存 > 0 且商品因归零下架的，自动恢复在售
- 订单状态不变

### 21. 仅退款 `POST /api/seller/after-sale/{afterSaleNo}/refund-only`

- 仅 `PENDING` 可操作，否则 40966
- 同事务内：原路退款 → 售后单 `REFUNDED`，`type` 置 `REFUND_ONLY`
- **不回补库存**（货不退）
- 退款金额 = 快照单价 × quantity

### 22. 拒绝售后 `POST /api/seller/after-sale/{afterSaleNo}/reject`

- 仅 `PENDING` 可操作，否则 40966
- 可选传 `reason`，系统不强制、不展示给客户（线下协商留痕在系统外）
- 转 `REJECTED`，不退款、不回补库存、订单状态不变
- 客户可再次申请，计入 3 次上限

---

## 七、数据表（新增 / 变更）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `payment` | `id`、`payment_no`、`order_no`、`channel`、`amount`、`status`、`trade_no`、`paid_at`、`refunded_at`、`created_at` | 一订单一笔有效支付；`status` 取 `PENDING` / `SUCCESS` / `REFUNDED` / `ABNORMAL` |
| `address` | `id`、`customer_id`、`receiver`、`phone`、`region`、`detail`、`is_default`、`created_at`、`updated_at` | 单客户 ≤ 10 条，`is_default` 同客户唯一 |
| `logistics` | `id`、`order_no`、`logistics_no`、`carrier`、`status`、`updated_at` | 一订单一条 |
| `after_sale` | `id`、`after_sale_no`、`order_no`、`customer_id`、`product_id`、`quantity`、`title`、`description`、`type`、`status`、`refund_amount`、`refund_channel`、`refunded_at`、`created_at` | 按商品 + 订单维度 |
| `after_sale_image` | `id`、`after_sale_id`、`url`、`created_at` | 最多 5 条/单 |

`order` 表新增字段：

| 字段 | 说明 |
|---|---|
| `address_snapshot` | 收货人、手机号、详细地址的快照文本或 JSON |
| `paid_at` | 支付成功时间 |
| `pay_channel` | 支付渠道冗余，便于列表展示 |
| `completed_at` | 交易完成时间 |
| `cancel_reason` | 取消原因（超时 / 客户取消 / 商家取消） |

---

## 八、错误码总表

| 码 | 含义 | 归属 |
|---|---|---|
| 40921 | 订单状态不允许该操作 | 订单域 |
| 40922 | 无权操作该订单 | 订单域 |
| 40923 | 订单已是终态 | 订单域 |
| 40924 | 库存不足 | 订单域 |
| 40925 | 必填参数为空或格式错误 | 通用 |
| 40926 | 商品已下架 | 订单域 |
| 40927 | 订单不存在 | 订单域 |
| 40941 | 订单不是待支付状态，无法支付 | 支付 |
| 40942 | 不支持的支付渠道 | 支付 |
| 40943 | 支付单已存在或重复支付 | 支付 |
| 40944 | 支付金额与订单金额不一致 | 支付 |
| 40945 | 支付记录不存在 | 支付 |
| 40951 | 地址不存在 | 地址 |
| 40952 | 地址数量已达上限（10） | 地址 |
| 40953 | 地址不属于当前客户 | 地址 |
| 40954 | 客户无收货地址，不能下单 | 地址 |
| 40961 | 售后单不存在 | 售后 |
| 40962 | 当前订单状态不可发起售后 | 售后 |
| 40963 | 售后数量超过购买数量 | 售后 |
| 40964 | 同一商品已有进行中的售后单 | 售后 |
| 40965 | 同一商品售后次数已达上限（3） | 售后 |
| 40966 | 售后单状态不允许该操作 | 售后 |
| 40967 | 售后单不属于当前客户 | 售后 |
| 40968 | 图片数量或大小超限 | 售后 |
| 40971 | 订单未发货，无物流信息 | 物流 |
| 40972 | 物流单号或快递公司为空 | 物流 |

---

## 九、前端对接要点

**买家端（童昕祺）**

- 下单前必须选地址，无地址跳地址管理页；下单只传 `addressId` + `items`
- 下单成功跳支付页，展示 `expireAt` 倒计时；剩 0 分钟时提示订单已关闭
- 支付返回页轮询 `GET /api/order/{orderNo}/pay`，成功后刷新订单详情
- 取消按钮显示条件：15 / 18 / 20 / 30 显示，40 及之后隐藏
- 「确认收货」按钮只在 40 显示
- 物流入口在 40 及之后显示；`status` 直接取 `statusText` 展示，不自己映射
- 售后入口只在 40 / 50 显示，按订单明细中的商品逐件发起

**卖家端（陈泽英）**

- 订单推进按钮：`confirm` 仅 18、`ready` 仅 20、`ship` 仅 30
- 发货弹窗必填物流单号与快递公司
- 取消按钮：15 / 18 / 20 / 30 / 40 显示，50 之后隐藏；18 及之后取消需二次确认，提示将原路退款
- 售后列表按状态筛选，详情页提供「同意退货」「仅退款」「拒绝」三个按钮
- 「确认收到退货」只在 `WAIT_RETURN` 显示，点击后弹二次确认（不可撤销）

---

## 十、未覆盖项

| 项 | 处理 |
|---|---|
| 运费、优惠券、部分退款 | 需求未定义，本期不做；退款金额固定为快照单价 × 数量 |
| 换货 | 任务要求只到退货 / 退款，本期不做 |
| 支付渠道真实对接 | 需求方不约束实现，沙箱或 Mock 均可，接口形状不变 |
| 物流轨迹数据来源 | 手动录入或第三方查询均可，对外字段一致 |
| 售后超时自动同意、商家响应时限 | 需求明确不做 |
| 售后拒绝理由留痕 | 需求明确不在系统内留痕 |
| 分包裹发货 | 需求明确一单一运单 |
