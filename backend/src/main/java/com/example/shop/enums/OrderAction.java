package com.example.shop.enums;

/**
 * 订单正向流转动作（B.1-1 的 4 条合法边）。
 * 每个动作只接受唯一的前置状态，其余一律 40921。
 */
public enum OrderAction {
    CONFIRM("confirm", OrderStatus.PENDING, OrderStatus.CONFIRMED),
    READY("ready", OrderStatus.CONFIRMED, OrderStatus.READY),
    SHIP("ship", OrderStatus.READY, OrderStatus.SHIPPED),
    COMPLETE("complete", OrderStatus.SHIPPED, OrderStatus.COMPLETED);

    private final String code;
    private final OrderStatus from;
    private final OrderStatus to;

    OrderAction(String code, OrderStatus from, OrderStatus to) {
        this.code = code;
        this.from = from;
        this.to = to;
    }

    public String getCode() { return code; }
    public OrderStatus getFrom() { return from; }
    public OrderStatus getTo() { return to; }

    public static OrderAction of(String code) {
        for (OrderAction a : values()) {
            if (a.code.equals(code)) return a;
        }
        return null;
    }
}
