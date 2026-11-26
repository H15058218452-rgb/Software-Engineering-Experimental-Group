-- =====================================================
-- 迭代 4（挑战需求 C）数据库迁移：买家收货地址 + 物流
--
-- 对应任务：
--   #22647 [C2-1] 买家收货地址表与接口
--   #22648 [C2-2] 物流单号与物流轨迹接口（模拟顺丰）
--
-- 编号说明：migration-h 留给支付/售后域（迭代 4 支付迁移），
--           本脚本使用 migration-i，避免两个分支同时新增 h 造成冲突。
--
-- 口径依据：《接口契约-挑战需求C》第八节（address: customer_id）
--           与第十节（logistics 一订单一条）
--
-- 特点（与 migration-a~g 一致）：
--   1. 完全幂等，可重复执行（每次启动都会跑）；
--   2. 不使用 DELIMITER / 存储过程，只用 information_schema 判断 + PREPARE 动态执行；
--   3. 只新增表与字段，不删除任何数据。
-- =====================================================

USE shop;

-- ---------- 1. 买家收货地址表（单客户 ≤ 10 条，默认地址唯一） ----------
CREATE TABLE IF NOT EXISTS address (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    customer_id BIGINT       NOT NULL COMMENT '所属客户 buyer.id',
    receiver    VARCHAR(20)  NOT NULL COMMENT '收货人',
    phone       VARCHAR(11)  NOT NULL COMMENT '收货人手机号',
    region      VARCHAR(50)  NOT NULL COMMENT '所在地区，如：浙江省杭州市西湖区',
    detail      VARCHAR(200) NOT NULL COMMENT '详细地址（街道、门牌）',
    is_default  TINYINT      NOT NULL DEFAULT 0 COMMENT '1 默认地址，同一客户最多一条',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_address_customer (customer_id, is_default, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '买家收货地址簿';

-- ---------- 2. 物流表：一个订单只有一条物流记录，不拆包裹 ----------
CREATE TABLE IF NOT EXISTS logistics (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(32) NOT NULL COMMENT '订单号，一单一运单',
    logistics_no VARCHAR(32) NOT NULL COMMENT '物流单号',
    carrier      VARCHAR(32) NOT NULL COMMENT '快递公司，如：顺丰速运',
    status       VARCHAR(16) NOT NULL DEFAULT 'SHIPPED' COMMENT 'SHIPPED/IN_TRANSIT/SIGNED',
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_logistics_order (order_no),
    KEY idx_logistics_no (logistics_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单物流信息';

-- ---------- 3. 兼容旧结构：若 address 表曾被旧脚本以 buyer_id 建过，补 customer_id ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE address ADD COLUMN customer_id BIGINT NULL COMMENT ''所属客户'' AFTER id',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'address' AND COLUMN_NAME = 'customer_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
