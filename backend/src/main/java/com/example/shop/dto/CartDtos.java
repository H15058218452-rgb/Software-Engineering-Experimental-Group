package com.example.shop.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车相关 DTO（需求 B2-1 / B2 购物车接口）。
 */
public interface CartDtos {

    /** 加入购物车：quantity 缺省为 1 */
    record AddRequest(
            @NotNull(message = "商品ID不能为空") Long productId,
            @NotNull(message = "数量不能为空") @Min(value = 1, message = "数量至少为1") Integer quantity) {
    }

    /** 修改购物车项：数量与勾选状态至少传一项 */
    record UpdateRequest(
            @Min(value = 1, message = "数量至少为1") Integer quantity,
            Integer selected) {
    }

    /** 购物车行：含商品快照信息，available 表示当前是否可下单 */
    record CartItem(Long cartId, Long productId, String productName, String imageUrl,
                    BigDecimal price, Integer stock, Integer quantity, Boolean selected,
                    BigDecimal subtotal, Integer productStatus, String productStatusText,
                    Boolean available) {
    }

    /** 购物车列表：合计只统计已勾选项 */
    record CartListResponse(List<CartItem> items, Integer itemCount, Integer totalQuantity,
                            BigDecimal selectedAmount, Integer selectedCount) {
    }

    /** 购物车转收藏：productIds 为空时默认取已勾选的商品 */
    record ToFavoriteRequest(List<Long> productIds) {
    }

    record ToFavoriteResponse(Integer moved, Integer skipped) {
    }

    /** 结算前校验：productIds 为空时默认校验全部购物车项 */
    record CheckoutCheckRequest(List<Long> productIds) {
    }

    record CheckoutCheckItem(Long productId, String productName, Integer quantity,
                             Integer stock, Boolean enough, String reason) {
    }

    record CheckoutCheckResponse(Boolean allEnough, List<CheckoutCheckItem> items) {
    }
}
