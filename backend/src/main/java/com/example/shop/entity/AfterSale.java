package com.example.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后申请单（迭代 4 · C.3-1 / C.3-2）。按「订单 + 商品」粒度，一条明细累计最多 3 次申请。
 *
 * type 由商家处理动作决定：同意退货置 RETURN_REFUND，仅退款置 REFUND_ONLY；
 * 申请阶段为 null，与契约示例一致。
 */
@TableName("after_sale")
public class AfterSale {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 售后单号，AS + yyyyMMdd + -#### */
    private String afterSaleNo;
    private String orderNo;
    /** order_item.id，同一订单同一商品只有一条明细 */
    private Long orderItemId;
    private Long productId;
    /** 商品名称快照，商品改名后售后单仍显示下单时的名称 */
    private String productName;
    private Long customerId;
    private Integer quantity;
    /** 售后问题标题，最长 50 字 */
    private String title;
    /** 问题描述，最长 500 字 */
    private String description;
    /** 凭证图片，JSON 字符串数组，最多 5 张 */
    private String images;
    /** RETURN_REFUND / REFUND_ONLY，申请阶段为 null */
    private String type;
    /** PENDING / WAIT_RETURN / REFUNDED / REJECTED / CANCELLED */
    private String status;
    /** 退款金额 = 快照单价 × quantity，退款前为 null */
    private BigDecimal refundAmount;
    /** 原路退款渠道，取自原支付单 */
    private String refundChannel;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAfterSaleNo() { return afterSaleNo; }
    public void setAfterSaleNo(String afterSaleNo) { this.afterSaleNo = afterSaleNo; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getRefundChannel() { return refundChannel; }
    public void setRefundChannel(String refundChannel) { this.refundChannel = refundChannel; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
