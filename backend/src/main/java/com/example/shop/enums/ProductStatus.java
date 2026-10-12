package com.example.shop.enums;

public enum ProductStatus {
    ON_SALE(0, "在售"),
    FROZEN(1, "手动冻结"),
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