-- =====================================================
-- 迭代 3 数据库迁移脚本（需求 B2-1）：新建购物车表 cart 与收藏表 favorite
--
-- 特点（与 migration-a~e 一致）：
--   1. 完全幂等：CREATE TABLE IF NOT EXISTS，可重复执行；
--   2. 只新增表，不修改既有表，不影响已有数据；
--   3. 唯一索引保证同一买家对同一商品只有一条记录。
-- =====================================================

USE shop;

-- 购物车项表：同一买家 + 商品唯一，重复加入时累加数量
CREATE TABLE IF NOT EXISTS cart (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    buyer_id   BIGINT      NOT NULL COMMENT '所属买家ID',
    product_id BIGINT      NOT NULL COMMENT '商品ID',
    quantity   INT         NOT NULL DEFAULT 1 COMMENT '数量，>=1 且不超过库存',
    selected   TINYINT     NOT NULL DEFAULT 1 COMMENT '1勾选用于结算 0未勾选',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_buyer_product (buyer_id, product_id),
    KEY idx_cart_buyer (buyer_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购物车表';

-- 收藏表：同一买家 + 商品唯一
CREATE TABLE IF NOT EXISTS favorite (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    buyer_id   BIGINT      NOT NULL COMMENT '所属买家ID',
    product_id BIGINT      NOT NULL COMMENT '商品ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite_buyer_product (buyer_id, product_id),
    KEY idx_favorite_buyer (buyer_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收藏表';
