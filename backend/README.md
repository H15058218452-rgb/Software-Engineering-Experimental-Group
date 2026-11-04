# 在线购物系统 MVP —— 后端

单卖家、免注册、线下交易的极简在线购物系统后端。负责商品状态机、排队队列、交易流转、鉴权与图片上传。

## 技术栈

- Java 17 + Spring Boot 3.3
- MyBatis-Plus 3.5（MySQL 8）
- JWT（jjwt）+ BCrypt（spring-security-crypto）
- springdoc-openapi（Swagger UI）

## 目录结构

```
backend/src/main/java/com/example/shop/
├── ShopApplication.java     # 启动类 + 默认卖家初始化
├── common/                  # 统一响应、错误码、异常、幂等、traceId、时间工具
├── config/                  # Jackson(Long→String/ISO时间)、Web(CORS/拦截器/静态资源)、MyBatis分页
├── auth/                    # JWT 工具 + 卖家鉴权拦截器
├── entity/                  # Seller / Product / Intent
├── enums/                   # ProductStatus / IntentStatus / Disposition
├── mapper/                  # 含 FOR UPDATE 行锁、队列查询、CAS 更新等自定义 SQL
├── dto/                     # 请求/响应 DTO（record）
├── service/                 # ProductService / BuyerService / SellerService / UploadService
└── controller/              # Product / Buyer / Seller(登录+视图) / Upload
```

## 运行步骤

### 1. 准备 MySQL

确保本机 MySQL 8 已启动，数据库连接信息见 `application.yml`（默认 `root/123456`，库名 `shop`）。

> 应用启动时会自动执行 `schema.sql`（全部 `CREATE TABLE IF NOT EXISTS`，幂等），无需手动建库建表；`shop` 库由脚本内 `CREATE DATABASE IF NOT EXISTS` 自动创建（需连接账号有建库权限）。

### 2. 启动

```bash
cd backend
mvn spring-boot:run
```

或打包后运行：

```bash
mvn -DskipTests package
java -jar target/online-shop-backend-1.0.0.jar
```

### 3. 访问

- API 根路径：`http://localhost:8080`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 图片静态访问：`http://localhost:8080/uploads/<filename>`

## 默认卖家账号

| 项 | 值 |
|---|---|
| 用户名 | `seller` |
| 密码 | `seller123456` |

可在 `application.yml` 的 `app.default-seller` 修改；首次启动时自动用 BCrypt 加密写入数据库。

## 端口约定（与契约第十章一致）

| 项目 | 端口 |
|---|---|
| 买家前端 | 5173 |
| 卖家前端 | 5174 |
| 后端 API | 8080 |
| MySQL | 3306 |

CORS 已放行 `http://localhost:5173` 与 `http://localhost:5174`。

## 快速联调示例

```bash
# 1. 卖家登录
curl -X POST http://localhost:8080/api/seller/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"seller\",\"password\":\"seller123456\"}"

# 2. 发布商品（token 从上一步获取）
curl -X POST http://localhost:8080/api/product/publish \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d "{\"name\":\"二手书\",\"price\":99.90,\"description\":\"九成新\"}"

# 3. 买家提交意向
curl -X POST http://localhost:8080/api/buyer/intent \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"张三\",\"phone\":\"13800000000\",\"requestId\":\"req-001\"}"
```

完整接口清单与请求/响应示例见 `docs/后端商品模块设计文档.md`。
