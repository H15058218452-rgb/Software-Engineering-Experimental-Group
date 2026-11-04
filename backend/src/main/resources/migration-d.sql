-- =====================================================
-- 迭代 3 数据库迁移脚本：意向单关联买家账号（需求 A.2-2 我的订单）
--
-- 特点（与 migration-a/b 一致）：
--   1. 完全幂等，可重复执行（每次启动都会跑）；
--   2. 不使用 DELIMITER / 存储过程，只用 information_schema 判断 + PREPARE 动态执行；
--   3. 不删除业务数据：老意向单 buyer_id 保持 NULL，查询时按手机号回退匹配。
-- =====================================================

USE shop;

-- ---------- 1. intent.buyer_id 下单买家ID ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE intent ADD COLUMN buyer_id BIGINT NULL COMMENT ''下单买家ID，NULL=注册前口令码意向'' AFTER product_id',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'intent' AND COLUMN_NAME = 'buyer_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. intent.phone 放开为选填（买家账号手机号本身选填，无手机号时存 NULL） ----------
SET @ddl = (SELECT IF(IS_NULLABLE = 'NO',
        'ALTER TABLE intent MODIFY COLUMN phone VARCHAR(20) NULL COMMENT ''买家联系电话，选填；无手机号时为 NULL''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'intent' AND COLUMN_NAME = 'phone'
    GROUP BY IS_NULLABLE);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. 我的订单查询索引（按买家 + 下单时间倒序） ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE intent ADD INDEX idx_intent_buyer (buyer_id, created_at)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'intent' AND INDEX_NAME = 'idx_intent_buyer');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
