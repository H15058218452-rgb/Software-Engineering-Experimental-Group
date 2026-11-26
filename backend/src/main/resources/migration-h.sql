-- =====================================================
-- 迭代 4（挑战需求 C）数据库迁移：订单状态扩展 + 支付域 + 售后域
--
-- 特点（与 migration-a~g 一致）：
--   1. 完全幂等，可重复执行（每次启动都会跑）；
--   2. 不使用 DELIMITER / 存储过程，只用 information_schema 判断 + PREPARE 动态执行；
--   3. 不删除任何业务数据：B 阶段订单原样保留，只补状态与时间字段；
--   4. 与《接口规范》第七节的两处实现偏差，接口返回形状与契约一致，仅存储方式不同：
--      a) order.pay_deadline：契约未列，为实现字段，超时取消任务依据它判断是否超期；
--      b) after_sale.images（JSON 数组）：承接契约 after_sale_image 子表的语义，不单独建子表。
--
-- 订单状态（作用于整单）：
--   10 客户下单（历史） / 15 待支付 / 18 已支付 / 20 商家确认
--   / 30 备货完成 / 40 已发货 / 50 交易完成 / 90 已取消
-- =====================================================

USE shop;

-- ---------- 1. order.pay_deadline 支付截止时间 ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `order` ADD COLUMN pay_deadline DATETIME(3) NULL COMMENT ''支付截止时间，下单后 24 小时'' AFTER status',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order' AND COLUMN_NAME = 'pay_deadline');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. order.paid_at 支付成功时间 ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `order` ADD COLUMN paid_at DATETIME(3) NULL COMMENT ''支付成功时间'' AFTER pay_deadline',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order' AND COLUMN_NAME = 'paid_at');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. 存量订单兜底 ----------
-- B 阶段的 10「客户下单」没有支付环节，C 阶段引入支付后统一转为 15「待支付」，
-- 给 24 小时支付窗口，避免历史订单卡在无法支付也不可确认的状态。
UPDATE `order`
   SET status = 15,
       pay_deadline = DATE_ADD(NOW(3), INTERVAL 24 HOUR)
 WHERE status = 10;

-- ---------- 4. 支付单表（契约第七节）----------
-- 一订单可有多条尝试记录，但同一时刻只允许一条 PENDING（由服务层保证）。
CREATE TABLE IF NOT EXISTS payment (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    payment_no   VARCHAR(32)    NOT NULL COMMENT '支付单号，PAY + yyyyMMddHHmmss + 3 位随机',
    order_no     VARCHAR(20)    NOT NULL COMMENT '关联订单号',
    customer_id  BIGINT         NOT NULL COMMENT '付款买家 buyer.id',
    amount       DECIMAL(10, 2) NOT NULL COMMENT '支付金额，等于订单总额快照',
    channel      VARCHAR(20)    NOT NULL COMMENT 'BANK_CARD/CREDIT_CARD/ALIPAY/WECHAT',
    status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/REFUNDED/ABNORMAL',
    trade_no     VARCHAR(64)    NULL COMMENT '渠道交易号，Mock 网关自造',
    paid_at      DATETIME(3)    NULL COMMENT '支付成功时间',
    refunded_at  DATETIME(3)    NULL COMMENT '退款时间',
    remark       VARCHAR(200)   NULL COMMENT '异常说明：回调金额不一致 / 模拟失败 / 订单已关闭',
    created_at   DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_payment_order (order_no, status),
    KEY idx_payment_customer (customer_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支付单表（Mock 网关）';

-- ---------- 5. 售后申请表（契约第七节）----------
-- 按「订单 + 商品」粒度，一条明细累计最多 3 次申请；type 由商家处理动作决定。
CREATE TABLE IF NOT EXISTS after_sale (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    after_sale_no  VARCHAR(24)    NOT NULL COMMENT '售后单号，AS + yyyyMMdd + -####',
    order_no       VARCHAR(20)    NOT NULL COMMENT '关联订单号',
    order_item_id  BIGINT         NOT NULL COMMENT 'order_item.id，售后按商品粒度',
    product_id     BIGINT         NOT NULL COMMENT '商品ID',
    product_name   VARCHAR(100)   NOT NULL DEFAULT '' COMMENT '商品名称快照',
    customer_id    BIGINT         NOT NULL COMMENT '申请买家 buyer.id',
    quantity       INT            NOT NULL DEFAULT 1 COMMENT '售后数量，不得超过购买数量',
    title          VARCHAR(50)    NOT NULL COMMENT '售后问题标题',
    description    VARCHAR(500)   NULL COMMENT '问题描述',
    images         TEXT           NULL COMMENT '凭证图片，JSON 字符串数组，最多 5 张',
    type           VARCHAR(20)    NULL COMMENT 'RETURN_REFUND / REFUND_ONLY，申请阶段为 NULL',
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/WAIT_RETURN/REFUNDED/REJECTED/CANCELLED',
    refund_amount  DECIMAL(10, 2) NULL COMMENT '退款金额 = 快照单价 * 数量，退款前为 NULL',
    refund_channel VARCHAR(20)    NULL COMMENT '原路退款渠道，取自原支付单',
    refunded_at    DATETIME(3)    NULL COMMENT '退款时间',
    created_at     DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sale_no (after_sale_no),
    KEY idx_after_sale_order (order_no),
    KEY idx_after_sale_customer (customer_id, created_at),
    KEY idx_after_sale_status (status, created_at),
    KEY idx_after_sale_item (order_item_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '售后申请表';
