package com.example.shop.enums;

/**
 * 订单正向流转动作（C.1-1）。
 *
 * 支付 15 → 18 由支付回调内部完成，不对外暴露为动作；
 * 交易完成 40 → 50 改由客户「确认收货」触发，商家无此权限。
 * 每个动作只接受唯一的前置状态，其余一律 40921。
 */
public enum OrderAction {
    CONFIRM("confirm", OrderStatus.PAID, OrderStatus.CONFIRMED),
    READY("ready", OrderStatus.CONFIRMED, OrderStatus.READY),
    SHIP("ship", OrderStatus.READY, OrderStatus.SHIPPED),
    RECEIVE("receive", OrderStatus.SHIPPED, OrderStatus.COMPLETED);

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
