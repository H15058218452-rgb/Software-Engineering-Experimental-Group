package com.example.shop.enums;

/**
 * 订单八态（迭代 4 · C.1-1）。整单流转，不存在部分状态。
 *
 * 正向 6 步：15 → 18 → 20 → 30 → 40 → 50
 * 终态 2 个：50 交易完成、90 已取消（无「交易失败」状态）
 *
 * 10「客户下单」是迭代 3 的历史状态，保留码值用于兼容存量数据，
 * 新订单一律从 15「待支付」开始。
 */
public enum OrderStatus {
    PENDING(10, "客户下单"),
    WAIT_PAY(15, "待支付"),
    PAID(18, "已支付"),
    CONFIRMED(20, "商家已确认"),
    READY(30, "备货完成"),
    SHIPPED(40, "已发货"),
    COMPLETED(50, "交易完成"),
    CANCELLED(90, "已取消");

    private final int code;
    private final String text;

    OrderStatus(int code, String text) {
        this.code = code;
        this.text = text;
    }

    public int getCode() { return code; }
    public String getText() { return text; }

    public static String textOf(Integer code) {
        if (code == null) return null;
        for (OrderStatus s : values()) {
            if (s.code == code) return s.text;
        }
        return null;
    }

    /** 终态：交易完成 / 已取消，之后不允许任何流转。 */
    public static boolean isFinal(Integer code) {
        return code != null
                && (code == COMPLETED.code || code == CANCELLED.code);
    }

    /** 已付款：18 已支付及之后、未取消，取消时需要走原路退款。 */
    public static boolean isPaid(Integer code) {
        return code != null && code >= PAID.code && code != CANCELLED.code;
    }

    /** 未发货：18 / 20 / 30，取消时需要回补库存。 */
    public static boolean isBeforeShip(Integer code) {
        return code != null
                && (code == PAID.code || code == CONFIRMED.code || code == READY.code);
    }

    /** 列表展示用的最终结果：终态返回 COMPLETED / CANCELLED，非终态返回 null。 */
    public static String finalResultOf(Integer code) {
        if (code == null) return null;
        if (code == COMPLETED.code) return "COMPLETED";
        if (code == CANCELLED.code) return "CANCELLED";
        return null;
    }
}
