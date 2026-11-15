-- =====================================================
-- 迭代 2（挑战需求 A）数据库迁移脚本：从基线结构升级到多商品结构
--
-- 特点：
--   1. 完全幂等，可重复执行（每次启动都会跑）；
--   2. 不使用 DELIMITER / 存储过程，只用 information_schema 判断 + PREPARE 动态执行，
--      避免 Spring 的 ScriptUtils 对分隔符的处理差异；
--   3. 不删除任何业务数据，旧商品库存补为 1。
-- =====================================================

USE shop;

-- ---------- 1. product.stock 库存字段 ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE product ADD COLUMN stock INT NOT NULL DEFAULT 1 COMMENT ''库存，>=0；成交扣减，归零自动下架'' AFTER price',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'stock');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. product.category_id 二级类别 ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE product ADD COLUMN category_id BIGINT NULL COMMENT ''二级类别ID'' AFTER name',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'category_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. product.images_json 商品图库 ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE product ADD COLUMN images_json TEXT NULL COMMENT ''商品图库，JSON 字符串数组，最多 9 张'' AFTER image_url',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'images_json');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 4. product.updated_at 更新时间 ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE product ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'updated_at');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 5. 去掉单活跃商品唯一索引 ----------
SET @ddl = (SELECT IF(COUNT(*) > 0,
        'ALTER TABLE product DROP INDEX uk_product_active',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND INDEX_NAME = 'uk_product_active');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 6. 去掉生成列 active_flag ----------
SET @ddl = (SELECT IF(COUNT(*) > 0,
        'ALTER TABLE product DROP COLUMN active_flag',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'active_flag');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 7. 描述字段扩容到 MEDIUMTEXT（富文本正文） ----------
ALTER TABLE product
    MODIFY COLUMN description MEDIUMTEXT NULL COMMENT '富文本描述（图文视频混排 HTML，已做白名单清洗）';

-- ---------- 8. 老数据的库存兜底（被扣成 0 的历史商品补 1，保证可交易） ----------
UPDATE product SET stock = 1 WHERE stock IS NULL OR stock = 0;

-- ---------- 9. 多商品列表所需索引（已存在则跳过） ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE product ADD INDEX idx_product_category (category_id, status)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND INDEX_NAME = 'idx_product_category');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
