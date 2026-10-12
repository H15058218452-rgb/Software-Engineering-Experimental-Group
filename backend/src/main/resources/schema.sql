-- =====================================================
-- 在线购物系统 MVP 数据库初始化脚本（幂等，可重复执行）
-- 状态枚举与契约文档保持一致：
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

CREATE TABLE IF NOT EXISTS product (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    name              VARCHAR(255)  NOT NULL COMMENT '商品名称（发布后不可修改）',
    description       TEXT          NULL COMMENT '商品描述（选填，发布后不可修改）',
    image_url         VARCHAR(500)  NULL COMMENT '商品主图地址（最多1张，JPG/PNG，<=5MB）',
    price             DECIMAL(10, 2) NOT NULL COMMENT '价格，>0，两位小数',
    status            TINYINT       NOT NULL DEFAULT 0 COMMENT '0在售 1手动冻结 2交易中冻结 3已下架',
    current_intent_id BIGINT        NULL COMMENT '当前交易中的意向ID，非交易中为NULL',
    created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    off_shelf_at      DATETIME(3)   NULL COMMENT '下架时间（已下架时非空）',
    active_flag       TINYINT GENERATED ALWAYS AS
                      (CASE WHEN status IN (0, 1, 2) THEN 1 ELSE NULL END) STORED
                      COMMENT '未下架标记，配合唯一索引实现单活跃商品约束',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_active (active_flag),
    KEY idx_product_status (status)
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
