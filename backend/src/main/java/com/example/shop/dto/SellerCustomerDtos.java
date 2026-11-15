package com.example.shop.dto;

/**
 * 卖家客户管理视图（A.2-3）：商家查看所有注册客户及其购买历史。
 * 购买历史通过 intent.phone 与 buyer.phone 关联（买家手机号为选填，未填则该客户无历史）。
 */
public interface SellerCustomerDtos {

    /** 客户列表项：注册信息 + 意向统计。 */
    record CustomerView(String id, String username, String phone, String nickname, String address,
                        String createdAt, Integer intentCount, Integer successCount) {
    }

    /** 客户的单条购买/意向记录：带商品快照信息。 */
    record CustomerIntentView(String intentId, String productId, String productName, String productPrice,
                              Integer status, String statusText, String queuedAt, String createdAt) {
    }
}
