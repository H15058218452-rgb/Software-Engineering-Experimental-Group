package com.example.shop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public interface ProductDtos {

    record PublishRequest(
            @NotBlank(message = "商品名称不能为空") @Size(max = 255) String name,
            @Size(max = 5000) String description,
            @NotNull(message = "价格不能为空")
            @DecimalMin(value = "0.01", message = "价格必须大于0")
            @Digits(integer = 8, fraction = 2, message = "价格最多8位整数2位小数") BigDecimal price,
            String imageUrl,
            String requestId) {
    }

    record ProductActionRequest(@NotNull(message = "商品ID不能为空") Long productId, String requestId) {
    }

    record StartTradeRequest(@NotNull(message = "商品ID不能为空") Long productId, String requestId) {
    }

    record ConfirmSoldRequest(
            @NotNull(message = "商品ID不能为空") Long productId,
            @NotNull(message = "意向ID不能为空") Long intentId,
            String requestId) {
    }

    record ConfirmFailedRequest(
            @NotNull(message = "商品ID不能为空") Long productId,
            @NotNull(message = "意向ID不能为空") Long intentId,
            @NotBlank(message = "处置方式不能为空") String disposition,
            String requestId) {
    }

    record IntentView(String id, String name, String phone, Integer status, String statusText,
                      Integer queuePosition, String queuedAt, String createdAt,
                      Boolean canModify, Boolean canCancel) {
    }

    record ProductView(String id, String name, String description, String imageUrl, String price,
                       Integer status, String statusText, String createTime, String offShelfTime,
                       Boolean canSubmitIntent) {
    }

    record SellerProductView(String id, String name, String description, String imageUrl, String price,
                             Integer status, String statusText, String createTime, String offShelfTime,
                             String currentIntentId,
                             Boolean canFreeze, Boolean canUnfreeze, Boolean canStartTrade,
                             Boolean canOffShelf, Boolean canConfirmTrade,
                             IntentView currentIntent, Integer intentCount) {
    }
}