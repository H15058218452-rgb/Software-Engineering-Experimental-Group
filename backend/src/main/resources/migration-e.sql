-- =====================================================
-- 迭代 3 数据库迁移脚本：手机号允许重复注册（一个手机号可注册多个买家账号）
--
-- 特点（与 migration-a/b/c/d 一致）：
--   1. 完全幂等，可重复执行（每次启动都会跑）；
--   2. 不使用 DELIMITER / 存储过程，只用 information_schema 判断 + PREPARE 动态执行；
--   3. 只去掉唯一约束，不删除数据：已存在的重复手机号账号不受影响。
-- =====================================================

USE shop;

-- ---------- 1. 去掉手机号唯一约束 ----------
SET @ddl = (SELECT IF(COUNT(*) > 0,
        'ALTER TABLE buyer DROP INDEX uk_buyer_phone',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buyer' AND INDEX_NAME = 'uk_buyer_phone');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. 补普通索引，保留按手机号查询的性能（卖家客户历史仍按手机号关联） ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE buyer ADD INDEX idx_buyer_phone (phone)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buyer' AND INDEX_NAME = 'idx_buyer_phone');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
