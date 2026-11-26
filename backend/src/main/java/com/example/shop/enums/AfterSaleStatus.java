package com.example.shop.enums;

/**
 * 售后单状态（C.3-1 / C.3-2 契约第六节，字符串枚举，前端直接取 statusText 展示）。
 *
 * PENDING     待处理 → 商家可同意退货 / 仅退款 / 拒绝，客户可撤销
 * WAIT_RETURN 待收货 → 商家确认收到退货后退款
 * REFUNDED    已退款（终态）
 * REJECTED    已拒绝（终态，客户可再次申请，计入 3 次上限）
 * CANCELLED   已撤销（终态，仅客户可操作）
 */
public enum AfterSaleStatus {
    PENDING("待处理"),
    WAIT_RETURN("待收货"),
    REFUNDED("已退款"),
    REJECTED("已拒绝"),
    CANCELLED("已撤销");

    private final String text;

    AfterSaleStatus(String text) {
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
        for (AfterSaleStatus s : values()) {
            if (s.name().equalsIgnoreCase(code.trim())) return s.text;
        }
        return null;
    }

    /** 处理中：待处理 / 待收货，同一订单明细只允许存在一个。 */
    public static boolean isActive(String code) {
        return PENDING.name().equals(code) || WAIT_RETURN.name().equals(code);
    }
}
