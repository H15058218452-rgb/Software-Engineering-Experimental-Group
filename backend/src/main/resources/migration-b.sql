-- =====================================================
-- 迭代 3 数据库迁移脚本：买家账号从「手机号登录」改为「用户名 + 密码登录」
--
-- 特点（与 migration-a.sql 一致）：
--   1. 完全幂等，可重复执行（每次启动都会跑）；
--   2. 不使用 DELIMITER / 存储过程，只用 information_schema 判断 + PREPARE 动态执行；
--   3. 不删除业务数据：老账号的用户名直接沿用手机号，密码哈希不变，仍可正常登录。
-- =====================================================

USE shop;

-- ---------- 1. buyer.username 登录用户名 ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE buyer ADD COLUMN username VARCHAR(20) NULL COMMENT ''登录用户名，唯一'' AFTER id',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buyer' AND COLUMN_NAME = 'username');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. 老数据兜底：用户名沿用手机号，没有手机号的用 buyer+id ----------
UPDATE buyer
   SET username = phone
 WHERE username IS NULL AND phone IS NOT NULL;

UPDATE buyer
   SET username = CONCAT('buyer', id)
 WHERE username IS NULL;

-- ---------- 3. username 收紧为 NOT NULL ----------
SET @ddl = (SELECT IF(IS_NULLABLE = 'YES',
        'ALTER TABLE buyer MODIFY COLUMN username VARCHAR(20) NOT NULL COMMENT ''登录用户名，唯一''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buyer' AND COLUMN_NAME = 'username'
    GROUP BY IS_NULLABLE);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 4. username 唯一索引 ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE buyer ADD UNIQUE KEY uk_buyer_username (username)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buyer' AND INDEX_NAME = 'uk_buyer_username');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 5. phone 放开为选填（唯一索引保留，MySQL 允许多个 NULL） ----------
SET @ddl = (SELECT IF(IS_NULLABLE = 'NO',
        'ALTER TABLE buyer MODIFY COLUMN phone VARCHAR(20) NULL COMMENT ''手机号，选填，用于关联意向单''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buyer' AND COLUMN_NAME = 'phone'
    GROUP BY IS_NULLABLE);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
