-- =====================================================
-- 在线购物系统 数据库初始化脚本（独立交付版 · 迭代 2）
-- 负责人：李炫苇（后端+数据库）
-- 用法：mysql -u root -p < shop_schema.sql
--
-- 说明：
-- 1. 本脚本为独立交付物，用于手动初始化/部署/评审演示。
-- 2. 表结构必须与 src/main/resources/schema.sql（Spring Boot 启动自动执行）保持一致，
--    任何变更需两处同步并群通知（契约规范第七条：禁止私自改字段不通知）。
-- 3. 脚本幂等，可重复执行（CREATE TABLE IF NOT EXISTS + INSERT IGNORE）。
-- 4. 从迭代 1 的旧库升级请执行 src/main/resources/migration-a.sql（幂等）。
-- 5. 演示数据见同目录 demo_data_a.sql（可选执行）。
--
-- 迭代 2（挑战需求 A）结构变更：
--   - 去掉 active_flag 生成列与 uk_product_active，取消「全局单活跃商品」约束，改为多商品各自独立队列；
--   - product 新增 category_id（二级类别）、stock（库存）、images_json（图库）、
--     description 扩为 MEDIUMTEXT（富文本 HTML）；
--   - 新增 category 表（固定两级）。
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
-- 2. 类别表 category（迭代 2 新增）
--    设计要点：
--    - 固定两级：parent_id 为 NULL 表示一级类别，非 NULL 表示二级类别；
--      不允许三级，由应用层拦截（给二级类别建子类直接拒绝）
--    - 同级名称唯一：uk_category_parent_name；MySQL 唯一索引不约束 NULL，
--      因此一级类别重名由应用层额外校验
--    - 有子类或有商品引用的类别不可删除（应用层校验）
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
--    设计要点（并发/状态机/库存）：
--    - status 为唯一业务权威（状态机），TINYINT 与三端枚举一致
--    - 迭代 2 移除 active_flag 与 uk_product_active：多商品可同时在售，
--      每件商品各自维护队列与当前交易（current_intent_id）
--    - stock：>=0；成交时在事务内扣减 1，扣到 0 自动置为已下架并关闭队列
--    - category_id 指向二级类别（一级类别不可直接挂商品）
--    - images_json：商品图库，JSON 字符串数组（最多 9 张），
--      image_url 冗余保存第一张作为主图，列表页只读主图
--    - description 为富文本 HTML（MEDIUMTEXT），入库前做白名单清洗
--    - 图片仅存相对 URL（/uploads/xxx），文件落盘 ./uploads 目录
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
--    - 队列排序统一 queued_at ASC, id ASC（契约规范第九条第 8 款），
--      idx_intent_queue 联合索引 (product_id, status, queued_at) 覆盖取队首查询；
--      迭代 2 多商品后，队列按 product_id 隔离，互不影响
--    - queued_at 用 DATETIME(3) 毫秒精度，保证先到先得不并列；
--      重新排队必须更新 queued_at 排到队尾（禁止事项第 7 条）
--    - 同一手机号同一商品仅一条有效意向（0/1/5）：应用层事务内查重
--    - 交易失败递补（作废/重新排队 + 队首递补）必须与商品状态更新同事务完成
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS intent (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    product_id BIGINT      NOT NULL COMMENT '所属商品ID',
    code_hash  CHAR(64)    NOT NULL COMMENT '口令码 SHA-256 哈希（不存明文）',
    name       VARCHAR(50) NOT NULL COMMENT '买家姓名',
    phone      VARCHAR(20) NOT NULL COMMENT '买家联系电话（后台可见，前台不展示）',
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
