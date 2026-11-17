-- =====================================================
-- 迭代 3（挑战需求 B）数据库迁移：订单域
--
-- 文件名说明：购物车/收藏表的脚本已占用 migration-f.sql，
--   本脚本让位改名为 migration-g.sql，避免合并时互相覆盖建表语句。
--
-- 特点（与 migration-a/b 一致）：
--   1. 完全幂等，可重复执行（每次启动都会跑）；
--   2. 不使用 DELIMITER / 存储过程，只用 information_schema 判断 + PREPARE 动态执行；
--   3. 不删除任何数据：迭代 2 的 intent 表整体保留为只读历史（B.1-5 裁定：旧意向不迁移、不删除）。
--
-- 订单状态（作用于整单，不支持部分流转）：
--   10 客户下单 / 20 商家确认 / 30 备货完成 / 40 已发货 / 50 交易完成 / 90 已取消
-- =====================================================

USE shop;

-- ---------- 1. 订单主表 ----------
-- order 是 MySQL 保留字，表名与 SQL 中一律使用反引号
CREATE TABLE IF NOT EXISTS `order` (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(20)   NOT NULL COMMENT '业务订单号，yyyyMMdd-####',
    customer_id  BIGINT        NOT NULL COMMENT '下单客户（buyer.id）',
    total_amount DECIMAL(10, 2) NOT NULL COMMENT '订单总金额 = 各明细小计之和',
    status       TINYINT       NOT NULL DEFAULT 10 COMMENT '10客户下单 20商家确认 30备货完成 40已发货 50交易完成 90已取消',
    created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_customer_time (customer_id, created_at),
    KEY idx_order_status_time (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单主表（单卖家自营，不设 seller_id）';

-- ---------- 2. 订单明细表 ----------
CREATE TABLE IF NOT EXISTS order_item (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    order_id     BIGINT         NOT NULL COMMENT 'order.id',
    product_id   BIGINT         NOT NULL COMMENT '商品ID',
    product_name VARCHAR(255)   NOT NULL COMMENT '下单时商品名称快照',
    price        DECIMAL(10, 2) NOT NULL COMMENT '下单时单价快照',
    quantity     INT            NOT NULL COMMENT '数量，>=1',
    subtotal     DECIMAL(10, 2) NOT NULL COMMENT 'price * quantity',
    PRIMARY KEY (id),
    KEY idx_item_order (order_id),
    KEY idx_item_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细表（商品快照，不随商品改名变化）';

-- ---------- 3. product.off_shelf_reason 下架原因 ----------
-- 订单扣库存到 0 自动下架时写 SOLD_OUT，取消回补后据此判断是否自动恢复在售
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE product ADD COLUMN off_shelf_reason VARCHAR(20) NULL COMMENT ''下架原因：SOLD 已售 / SOLD_OUT 库存耗尽 / MANUAL 手动'' AFTER off_shelf_at',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'off_shelf_reason');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史已下架商品没有原因，统一按手动下架兜底，避免回补时被误判为自动恢复
UPDATE product SET off_shelf_reason = 'MANUAL' WHERE status = 3 AND off_shelf_reason IS NULL;

-- ---------- 4. 旧意向数据留档（B.1-6：不迁移、不删除，仅停止写入）----------
-- 说明：intent 表结构与数据均保留；迭代 3 起订单域不再读写 intent，
-- 商品表 current_intent_id 字段保留但不再写入，此处不做任何删改。
