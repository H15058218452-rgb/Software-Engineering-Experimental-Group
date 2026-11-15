package com.example.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public interface BuyerDtos {

    /**
     * 提交购买意向。
     * 迭代 3 起身份由登录 Token 识别：name/phone 可选，未传时取买家账号昵称/用户名与手机号；
     * 无 Token 的旧调用仍要求 name/phone（服务层校验）。
     */
    record SubmitIntentRequest(
            @Size(max = 50, message = "姓名长度不能超过50个字符") String name,
            @Size(max = 20, message = "联系电话长度不能超过20个字符") String phone,
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

    /**
     * 我的订单（历史下单记录）行数据（需求 A.2-2）。
     * 只暴露商品名称、下单时间、交易结果、交易时间；不含价格/图片/卖家信息。
     */
    record OrderItem(String orderId, String productName, String createTime,
                     Integer status, String statusText, String dealTime) {
    }
}
