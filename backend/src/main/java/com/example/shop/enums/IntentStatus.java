package com.example.shop.enums;

public enum IntentStatus {
    QUEUING(0, "排队中"),
    TRADING(1, "交易中"),
    SUCCESS(2, "交易成功"),
    VOIDED(3, "已作废"),
    CANCELLED(4, "已撤销"),
    REQUEUED(5, "重新排队中"),
    FINAL_FAILED(6, "最终失败");

    private final int code;
    private final String text;

    IntentStatus(int code, String text) {
        this.code = code;
        this.text = text;
    }

    public int getCode() { return code; }
    public String getText() { return text; }

    public static String textOf(Integer code) {
        if (code == null) return null;
        for (IntentStatus s : values()) {
            if (s.code == code) return s.text;
        }
        return null;
    }
}