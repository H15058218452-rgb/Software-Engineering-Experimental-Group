package com.example.shop.enums;

/**
 * 支付渠道（C.1-1 契约）：四选一，不支持组合支付，也不做渠道余额。
 * MVP 阶段全部由 Mock 网关模拟，回调由 /api/pay/callback 触发。
 */
public enum PayChannel {
    BANK_CARD("BANK_CARD", "银行卡"),
    CREDIT_CARD("CREDIT_CARD", "信用卡"),
    ALIPAY("ALIPAY", "支付宝"),
    WECHAT("WECHAT", "微信支付");

    private final String code;
    private final String text;

    PayChannel(String code, String text) {
        this.code = code;
        this.text = text;
    }

    public String getCode() { return code; }
    public String getText() { return text; }

    /** 渠道码校验：null 或非法码返回 null，由调用方转 40942。 */
    public static PayChannel of(String code) {
        if (code == null) return null;
        for (PayChannel c : values()) {
            if (c.code.equalsIgnoreCase(code.trim())) return c;
        }
        return null;
    }

    public static String textOf(String code) {
        PayChannel c = of(code);
        return c == null ? null : c.text;
    }
}
