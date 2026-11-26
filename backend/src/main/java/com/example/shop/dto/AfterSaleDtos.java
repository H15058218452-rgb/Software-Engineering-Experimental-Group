package com.example.shop.dto;

import java.util.List;

/**
 * 售后域 DTO（C.3-1 / C.3-2 契约第六节）。金额、ID 一律字符串，时间 ISO 8601，状态用字符串枚举。
 */
public interface AfterSaleDtos {

    /**
     * 发起售后：按订单 + 商品定位明细。
     * quantity 省略时默认该明细的全部购买数量；images 最多 5 个已上传图片地址。
     */
    record CreateRequest(String orderNo, String productId, Integer quantity, String title,
                         String description, List<String> images) {
    }

    /** 售后单视图：列表与详情共用，退款前三项为 null。 */
    record AfterSaleView(String afterSaleNo, String orderNo, String productId, String productName,
                         Integer quantity, String title, String description, List<String> images,
                         String type, String status, String statusText, String refundAmount,
                         String refundChannel, String refundedAt, String createdAt) {
    }

    record AfterSalePage(List<AfterSaleView> list, long total, int page, int size) {
    }

    /** 状态流转返回：{ afterSaleNo, status, statusText }。 */
    record StatusView(String afterSaleNo, String status, String statusText) {
    }

    /** 确认收到退货入参，refundNote 仅作线下留痕，系统不落库展示。 */
    record ReceiveRequest(String refundNote) {
    }

    /** 拒绝售后入参，reason 系统不强制、不展示给客户。 */
    record RejectRequest(String reason) {
    }
}
