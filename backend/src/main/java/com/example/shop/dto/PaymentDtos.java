package com.example.shop.dto;

/**
 * 支付域 DTO（C.1-1 / C.1-2 契约第二、三节）。金额与 ID 一律字符串，时间 ISO 8601。
 */
public interface PaymentDtos {

    /** 发起支付入参：渠道码四选一。 */
    record PayRequest(String channel) {
    }

    /** 发起支付返回：paymentNo + payUrl + 倒计时截止时间。 */
    record PayView(String paymentNo, String orderNo, String channel, String amount,
                   String payUrl, String expireAt) {
    }

    /** 查询支付结果（收银台轮询）：直接给出订单状态，前端不必自己映射。 */
    record PayQueryView(String orderNo, Integer orderStatus, String statusText, String paymentNo,
                        String channel, String amount, String paidAt) {
    }

    /** Mock 网关回调入参。success 缺省视为成功，failReason 仅用于 Mock 演示失败分支。 */
    record CallbackRequest(String paymentNo, String channel, String amount, String tradeNo,
                           String paidAt, Boolean success, String failReason) {
    }

    /** 回调处理结果：支付单状态 + 订单当前状态。 */
    record CallbackView(String paymentNo, String orderNo, String payStatus, Integer orderStatus) {
    }
}
