-- =====================================================
-- 迭代 3（挑战需求 C）数据库迁移：买家收货地址 + 物流单号
--
-- 对应任务：
--   #22647 [C2-1] 买家收货地址表与接口
--   #22648 [C2-2] 物流单号与物流轨迹接口（模拟顺丰）
--
-- 特点（与 migration-a~g 一致）：
--   1. 完全幂等，可重复执行（每次启动都会跑）；
--   2. 不使用 DELIMITER / 存储过程，只用 information_schema 判断 + PREPARE 动态执行；
--   3. 只新增表与字段，不删除任何数据。
-- =====================================================

USE shop;

-- ---------- 1. 买家收货地址表 ----------
-- 与 buyer.address（默认交易地点）区分：这里是可维护多条、可设默认的收货地址簿
CREATE TABLE IF NOT EXISTS address (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    buyer_id       BIGINT       NOT NULL COMMENT '所属买家 buyer.id',
    receiver_name  VARCHAR(32)  NOT NULL COMMENT '收货人',
    receiver_phone VARCHAR(20)  NOT NULL COMMENT '收货人手机号',
    province       VARCHAR(32)  NOT NULL COMMENT '省',
    city           VARCHAR(32)  NOT NULL COMMENT '市',
    district       VARCHAR(32)  NULL     COMMENT '区/县',
    detail         VARCHAR(200) NOT NULL COMMENT '详细地址（街道、门牌）',
    is_default     TINYINT      NOT NULL DEFAULT 0 COMMENT '1 默认地址，同一买家最多一条',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_address_buyer (buyer_id, is_default, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '买家收货地址簿';

-- ---------- 2. 订单物流字段（卖家发货填单号） ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `order` ADD COLUMN tracking_no VARCHAR(32) NULL COMMENT ''物流单号'' AFTER status',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order' AND COLUMN_NAME = 'tracking_no');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `order` ADD COLUMN carrier VARCHAR(16) NULL COMMENT ''物流公司，如 SF 顺丰'' AFTER tracking_no',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order' AND COLUMN_NAME = 'carrier');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `order` ADD COLUMN shipped_at DATETIME(3) NULL COMMENT ''发货时间，物流轨迹起点'' AFTER carrier',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order' AND COLUMN_NAME = 'shipped_at');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. 按物流单号查询索引 ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `order` ADD UNIQUE KEY uk_order_tracking (tracking_no)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order' AND INDEX_NAME = 'uk_order_tracking');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
