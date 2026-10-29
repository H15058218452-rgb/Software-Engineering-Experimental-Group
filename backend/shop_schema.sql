-- =====================================================
-- 在线购物系统 数据库初始化脚本（独立交付版）
-- 负责人：李炫苇（后端+数据库）
-- 用法：mysql -u root -p < shop_schema.sql
--
-- 说明：
-- 1. 本脚本为独立交付物，用于手动初始化/部署/评审演示（全新安装）。
-- 2. 表结构与 src/main/resources/schema.sql（Spring Boot 启动自动执行）逐字一致，
--    任何变更需两处同步并群通知（契约规范第七条：禁止私自改字段不通知）。
-- 3. 从迭代 1 旧库升级请勿用本脚本，执行 src/main/resources/migration-a.sql
--    （幂等迁移：加库存/类别/图库字段、去单活跃约束、老数据库存兜底补 1）。
-- 4. 脚本幂等，可重复执行（CREATE TABLE IF NOT EXISTS + INSERT IGNORE）。
-- 5. 默认卖家账号若不通过本脚本插入，应用首次启动时也会自动创建
--    （账号取自 application.yml 的 app.default-seller 配置）。
--
-- 状态枚举与契约文档保持一致（前端、后端、数据库三端统一）：
--   商品 status: 0 在售 / 1 手动冻结 / 2 交易中冻结 / 3 已下架
--   意向 status: 0 排队中 / 1 交易中 / 2 交易成功 / 3 已作废
--               / 4 已撤销 / 5 重新排队中 / 6 最终失败
--   交易失败处置: VOID 作废 / REQUEUE 重新排队
-- =====================================================

CREATE DATABASE IF NOT EXISTS shop DEFAULT CHARACTER SET utf8mb4;
USE shop;

-- -----------------------------------------------------
-- 1. 卖家表 seller
--    设计要点：
--    - 固定单账号，username 唯一索引兼做幂等插入依据
--    - 密码仅存 BCrypt 哈希（spring-security-crypto），不存明文
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS seller (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL COMMENT '登录用户名（固定单账号）',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密密码',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_seller_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '卖家表';

-- -----------------------------------------------------
-- 2. 商品两级类别表 category（迭代 2 新增）
--    设计要点：
--    - parent_id 为 NULL 表示一级类别；业务层限制只允许两级
--    - uk_category_parent_name 保证同级下类别名不重复
--    - idx_category_parent 加速「按一级类别取子类列表」
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS category (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL COMMENT '类别名称',
    parent_id  BIGINT      NULL COMMENT '上级类别ID，NULL=一级类别',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_category_parent (parent_id, sort),
    UNIQUE KEY uk_category_parent_name (parent_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品两级类别表';

-- -----------------------------------------------------
-- 3. 商品表 product
--    设计要点（迭代 2：多商品并存，一商品一队列）：
--    - 迭代 2 去掉「单活跃商品」唯一约束（active_flag 生成列 + uk_product_active
--      已随 migration-a.sql 下线），允许多个商品同时在售，每个商品独立排队
--    - status 为唯一业务权威（状态机），TINYINT 与三端枚举一致
--    - stock 库存 >= 0，成交扣减，归零自动下架（状态置 3）
--    - category_id 指向二级类别，idx_product_category 支撑分类列表页
--    - images_json 为商品图库（JSON 字符串数组，最多 9 张），
--      image_url 主图始终等于图库第一张
--    - description 扩容为 MEDIUMTEXT，承载富文本图文视频混排 HTML（白名单清洗后入库）
--    - current_intent_id 记录本商品交易中队首意向，非交易中为 NULL
--    - price DECIMAL(10,2)：金额严禁用 JS number，前端以字符串传输
--    - 图片仅存相对 URL（/uploads/xxx），图库图限 JPG/PNG、单张 <=5MB；
--      富文本内嵌媒体（图片/视频）不做单独限制，仅受传输层上限约束
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS product (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    name              VARCHAR(255)  NOT NULL COMMENT '商品名称',
    category_id       BIGINT        NULL COMMENT '二级类别ID',
    description       MEDIUMTEXT    NULL COMMENT '富文本描述（图文视频混排 HTML，已做白名单清洗）',
    image_url         VARCHAR(500)  NULL COMMENT '主图地址（等于图库第一张）',
    images_json       TEXT          NULL COMMENT '商品图库，JSON 字符串数组，最多 9 张',
    price             DECIMAL(10, 2) NOT NULL COMMENT '价格，>0，两位小数',
    stock             INT           NOT NULL DEFAULT 0 COMMENT '库存，>=0；成交扣减，归零自动下架',
    status            TINYINT       NOT NULL DEFAULT 0 COMMENT '0在售 1手动冻结 2交易中冻结 3已下架',
    current_intent_id BIGINT        NULL COMMENT '当前交易中的意向ID，非交易中为NULL',
    created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    off_shelf_at      DATETIME(3)   NULL COMMENT '下架时间（已下架时非空）',
    PRIMARY KEY (id),
    KEY idx_product_status (status, created_at),
    KEY idx_product_category (category_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品表';

-- -----------------------------------------------------
-- 4. 买家意向表 intent（排队队列）
--    设计要点（并发/队列）：
--    - 口令码为买家唯一身份凭证，库中只存 SHA-256 哈希（code_hash），
--      明文仅提交成功页展示一次；uk_intent_code 唯一索引兼防重放
--    - 迭代 2 起队列为「每商品一条」：product_id 必填，排序统一
--      queued_at ASC, id ASC，idx_intent_queue 联合索引 (product_id, status, queued_at)
--      覆盖取队首查询
--    - queued_at 用 DATETIME(3) 毫秒精度，保证先到先得不并列；
--      重新排队必须更新 queued_at 排到队尾（禁止事项第 7 条）
--    - 同一手机号同一商品仅一条有效意向（0/1/5）：应用层事务内查重，
--      idx_intent_product_phone 加速查重与后台展示（撤销/作废后允许重新提交，
--      故不能建数据库唯一索引）
--    - 交易失败递补（作废/重新排队 + 队首递补）必须与商品状态更新同事务完成
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS intent (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    product_id BIGINT      NOT NULL COMMENT '所属商品ID',
    code_hash  CHAR(64)    NOT NULL COMMENT '口令码 SHA-256 哈希（不存明文）',
    name       VARCHAR(50) NOT NULL COMMENT '买家姓名',
    phone      VARCHAR(20) NOT NULL COMMENT '买家联系电话',
    status     TINYINT     NOT NULL DEFAULT 0 COMMENT '0排队中 1交易中 2交易成功 3已作废 4已撤销 5重新排队中 6最终失败',
    queued_at  DATETIME(3) NOT NULL COMMENT '排队时间，重新排队时更新为当前时间（排到队尾）',
    fail_count INT         NOT NULL DEFAULT 0 COMMENT '交易失败次数',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_intent_code (code_hash),
    KEY idx_intent_queue (product_id, status, queued_at),
    KEY idx_intent_product_phone (product_id, phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '买家意向表（排队队列）';

-- -----------------------------------------------------
-- 5. 种子数据：默认卖家账号
--    用户名 seller，密码 seller123456（BCrypt 哈希，与 application.yml 默认配置一致）
--    INSERT IGNORE 保证幂等；应用启动时也会自动补建该账号
-- -----------------------------------------------------
INSERT IGNORE INTO seller (username, password_hash)
VALUES ('seller', '$2a$10$l97mZQdXF2qw7fNj2DUAmuEnH3.uyhq1I29TbEHNigjyycy40et1q');
