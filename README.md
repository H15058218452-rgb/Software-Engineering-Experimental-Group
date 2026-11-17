# 单商家在线购物系统（挑战B升级版）

单卖家、多商品同时在售的在线购物系统。买家注册登录后，对商品提交购买意向（加入购物车/收藏），按商品独立排队；卖家在后台管理订单状态机、商品库存并完成线下交易确认。

- **后端**：Java 17 + Spring Boot 3.3 + MySQL 8.0
- **前端**：Vue 3 + Vite + Element Plus
- **接口风格**：REST / JSON

## 功能概览

| 模块 | 能力 |
|---|---|
| 商品浏览 | 多商品列表、商品详情、关键词搜索、二级类别筛选 |
| 客户账号 | 注册、登录、个人资料、历史订单查询 |
| 购买意向 | 登录后提交意向；每个商品独立队列、先到先得；同一客户同一商品仅一个有效意向 |
| 购物车与收藏 | 支持多件商品加入购物车合并下单、多件商品移入收藏夹 (挑战B新增) |
| 库存 | 库存非负整数；成交扣减；库存归零自动售罄下架，排队意向同步作废；补货后恢复在售 |
| 订单流转 | 支持5种状态：客户下单、商家确认、备货完成、开始发货、交易完成；支持取消订单及库存回滚 (挑战B新增) |
| 商家后台 | 商品发布、多图与富媒体描述、类别维护、库存调整、上下架 |
| 历史与台账 | 客户历史下单、商家历史商品交易链路、客户台账 |
| 操作日志 | 登录、改密、商品、类别、库存、交易等关键操作留痕 |

商品不提供编辑功能；商品必须挂在二级类别下；库存为 0 的商品不会出现在公开列表。

## 仓库结构

| 路径 | 说明 |
|---|---|
| backend/ | Java 后端（Spring Boot + MySQL） |
| frontend/ | 前端（Vue 3 + Vite） |
| sql/ | 数据库初始化建表脚本（shop_schema.sql） |
| scripts/ | API 冒烟测试脚本 |
| tests/ | 自动化接口回归测试集 (Thunder Client JSON) |
| docs/ | 技术栈、数据库、接口文档与阶段成果文档 |

## 后端快速开始

环境要求：JDK 17、Maven 3.8+、MySQL 8.0。

1. 初始化数据库：请先在 MySQL 中创建一个名为 shop 的数据库，然后运行 sql/shop_schema.sql 脚本建表。
2. 启动后端：

cd backend
mvn spring-boot:run

- 服务地址：http://127.0.0.1:8080
- 默认卖家账号：seller / seller123456（首次启动自动写入）
- 上传文件目录：backend/uploads/

### 运行测试

cd backend
mvn test

此外，我们还提供了基于 PowerShell 的 32 步 API 冒烟测试脚本，位于 scripts/api-smoke-test.ps1。

## 前端快速开始

环境要求：Node.js 18+。

cd frontend
npm install
npm run dev

- 开发地址：http://localhost:5173
- 开发期由 Vite 将 /api、/uploads 代理到 http://localhost:8080
- 生产构建：npm run build，产物在 frontend/dist/

## 接口与文档

- 构建部署手册：[点击查看 Wiki](https://github.com/H15058218452-rgb/Software-Engineering-Experimental-Group/wiki/构建部署手册)
- 数据库设计文档：[sql/shop_schema.sql](sql/shop_schema.sql)
- 挑战B测试用例：[点击查看 Wiki](https://github.com/H15058218452-rgb/Software-Engineering-Experimental-Group/wiki/挑战需求B-测试用例)

## 并发说明

后端基于 Spring Boot 事务控制与 MySQL 行级锁（或乐观锁）处理并发。在购物车合并下单及订单状态流转时，保证库存扣减的原子性与状态机的一致性，防止超卖。
