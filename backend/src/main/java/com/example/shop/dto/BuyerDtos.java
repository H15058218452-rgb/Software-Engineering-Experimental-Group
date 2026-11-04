package com.example.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public interface BuyerDtos {

    /**
     * 提交购买意向。
     * 迭代 2 起必须带 productId（多商品各自独立队列）；传空时兼容基线，取最近一件在售商品。
     */
    record SubmitIntentRequest(
            @NotBlank(message = "姓名不能为空") @Size(max = 50) String name,
            @NotBlank(message = "联系电话不能为空") @Size(max = 20) String phone,
            Long productId,
            String requestId) {
    }

    record SubmitIntentResponse(String code, ProductDtos.IntentView intent) {
    }

    record ModifyIntentRequest(@NotBlank(message = "口令码不能为空") String code, String name, String phone) {
    }

    record CancelIntentRequest(@NotBlank(message = "口令码不能为空") String code) {
    }

    record BuyerIntentView(String intentId, Integer intentStatus, String intentStatusText,
                           Integer queuePosition, Boolean canModify, Boolean canCancel,
                           String name, String phone, String queuedAt,
                           Integer productStatus, String productStatusText,
                           String productName, String productPrice, String createdAt) {
    }
}
