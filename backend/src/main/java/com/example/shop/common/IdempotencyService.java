package com.example.shop.common;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class IdempotencyService {

    private static final long TTL_MS = 10 * 60 * 1000L;

    private record Entry(Object value, long expireAt) {
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> ApiResponse<T> execute(String key, Supplier<ApiResponse<T>> action) {
        if (key == null || key.isBlank()) {
            return action.get();
        }
        cleanUp();
        Entry existing = cache.get(key);
        if (existing != null) {
            if (existing.value() != null) {
                return (ApiResponse<T>) existing.value();
            }
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT.getCode(), "请求正在处理中，请勿重复提交");
        }
        cache.put(key, new Entry(null, System.currentTimeMillis() + TTL_MS));
        try {
            ApiResponse<T> result = action.get();
            cache.put(key, new Entry(result, System.currentTimeMillis() + TTL_MS));
            return result;
        } catch (RuntimeException e) {
            cache.remove(key);
            throw e;
        }
    }

    private void cleanUp() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expireAt() < now) {
                it.remove();
            }
        }
    }
}