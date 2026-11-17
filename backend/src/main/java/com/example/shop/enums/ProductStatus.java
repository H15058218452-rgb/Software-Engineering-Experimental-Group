package com.example.shop.enums;

/**
 * 商品状态。
 *
 * 迭代 3（B.1-5 裁定）：购买意向机制下线后，商品状态由四态收敛为两态——
 * 业务上只有「在售 0」与「已下架 3」，且不再存在「上架 / 下架」按钮触发的人工冻结流转。
 *
 * FROZEN、TRADING 仅保留用于读取迭代 2 的历史数据与历史订单展示，
 * 迭代 3 起任何新流程都不得再写入 1 / 2（下单只接受 0，扣到 0 自动转 3）。
 */
public enum ProductStatus {
    ON_SALE(0, "在售"),
    /** @deprecated 迭代 2 遗留，逻辑删除的冻结态，B 阶段不再写入 */
    @Deprecated
    FROZEN(1, "手动冻结"),
    /** @deprecated 迭代 2 遗留的意向交易冻结态，B 阶段不再写入 */
    @Deprecated
    TRADING(2, "交易中冻结"),
    OFF_SHELF(3, "已下架");

    private final int code;
    private final String text;

    ProductStatus(int code, String text) {
        this.code = code;
        this.text = text;
    }

    public int getCode() { return code; }
    public String getText() { return text; }

    public static String textOf(Integer code) {
        if (code == null) return null;
        for (ProductStatus s : values()) {
            if (s.code == code) return s.text;
        }
        return null;
    }
}