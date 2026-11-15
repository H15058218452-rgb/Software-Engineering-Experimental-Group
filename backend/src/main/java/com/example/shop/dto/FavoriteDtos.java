package com.example.shop.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 收藏夹相关 DTO（需求 B2-2 收藏夹基础接口）。
 */
public interface FavoriteDtos {

    record AddRequest(@NotNull(message = "商品ID不能为空") Long productId) {
    }

    record FavoriteItem(Long favoriteId, Long productId, String productName, String imageUrl,
                        BigDecimal price, Integer productStatus, String productStatusText,
                        Boolean available, String createdAt) {
    }
}
