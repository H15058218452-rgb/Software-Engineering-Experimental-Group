-- =====================================================
-- 在线购物系统 数据库初始化脚本（幂等，可重复执行）
-- 迭代 2（挑战需求 A）版本：支持多商品、两级类别、库存、图集与富文本描述
--
-- 状态枚举：
--   商品 status: 0 在售 / 1 手动冻结 / 2 交易中冻结 / 3 已下架
--   意向 status: 0 排队中 / 1 交易中 / 2 交易成功 / 3 已作废
--               / 4 已撤销 / 5 重新排队中 / 6 最终失败
-- =====================================================

CREATE DATABASE IF NOT EXISTS shop DEFAULT CHARACTER SET utf8mb4;
USE shop;

CREATE TABLE IF NOT EXISTS seller (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL COMMENT '登录用户名（固定单账号）',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密密码',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_seller_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '卖家表';

-- 两级类别（parent_id 为 NULL 表示一级类别；只允许两级）
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

-- 商品表：迭代 2 去掉「单活跃商品」唯一约束，改为一商品一队列；新增库存/类别/图库/富文本
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
