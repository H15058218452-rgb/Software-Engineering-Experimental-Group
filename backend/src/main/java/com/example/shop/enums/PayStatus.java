package com.example.shop.enums;

/**
 * 支付单状态（C.1-1 契约第七节：payment.status 取 PENDING / SUCCESS / REFUNDED / ABNORMAL）。
 *
 * PENDING  待支付：同一订单同时只允许一张
 * SUCCESS  支付成功：订单随之推进到 18 已支付
 * REFUNDED 已退款：订单取消或售后退款后置为该状态
 * ABNORMAL 异常：支付失败、金额与订单不一致等，订单保持 15 可重新发起
 */
public enum PayStatus {
    PENDING("待支付"),
    SUCCESS("支付成功"),
    REFUNDED("已退款"),
    ABNORMAL("支付异常");

    private final String text;

    PayStatus(String text) {
        this.text = text;
    }

    public String getCode() {
        return name();
    }

    public String getText() {
        return text;
    }

    public static String textOf(String code) {
        if (code == null) return null;
        for (PayStatus s : values()) {
            if (s.name().equalsIgnoreCase(code.trim())) return s.text;
        }
        return null;
    }
}
