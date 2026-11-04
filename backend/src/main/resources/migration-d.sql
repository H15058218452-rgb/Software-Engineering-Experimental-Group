-- =====================================================
-- 迭代 3 数据库迁移脚本：买家默认交易地点（卖家客户管理展示）
--
-- 特点（与 migration-a/b/c 一致）：
--   1. 完全幂等，可重复执行（每次启动都会跑）；
--   2. 不使用 DELIMITER / 存储过程，只用 information_schema 判断 + PREPARE 动态执行；
--   3. 不删除业务数据：老买家 address 保持 NULL，前端显示为 '-'。
-- =====================================================

USE shop;

-- buyer.address 默认交易地点（注册时选填）
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE buyer ADD COLUMN address VARCHAR(100) NULL COMMENT ''默认交易地点，选填'' AFTER nickname',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buyer' AND COLUMN_NAME = 'address');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
