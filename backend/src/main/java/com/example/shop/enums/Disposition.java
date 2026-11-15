package com.example.shop.enums;

public enum Disposition {
    VOID("VOID"),
    REQUEUE("REQUEUE");

    private final String value;

    Disposition(String value) {
        this.value = value;
    }

    public String getValue() { return value; }

    public static Disposition from(String value) {
        for (Disposition d : values()) {
            if (d.value.equalsIgnoreCase(value)) return d;
        }
        return null;
    }
}