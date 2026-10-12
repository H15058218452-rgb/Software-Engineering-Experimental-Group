package com.example.shop.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private TimeUtil() {
    }

    public static String format(LocalDateTime t) {
        return t == null ? null : t.format(F) + "+08:00";
    }
}