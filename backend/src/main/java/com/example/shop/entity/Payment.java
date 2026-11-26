package com.example.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单（迭代 4 · C.1-1 契约第七节）。
 *
 * 一订单一笔有效支付：同一订单同一时刻只允许一张 PENDING 单，重复发起支付直接返回原单号。
 * 支付失败 / 金额不一致置 ABNORMAL，订单保持 15，客户可重新发起（生成新单）。
 */
@TableName("payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 支付单号，PAY + yyyyMMddHHmmss + 3 位随机 */
    private String paymentNo;
    private String orderNo;
    private Long customerId;
    private BigDecimal amount;
    /** BANK_CARD / CREDIT_CARD / ALIPAY / WECHAT */
    private String channel;
    /** PENDING / SUCCESS / REFUNDED / ABNORMAL */
    private String status;
    /** 渠道交易号，Mock 网关自造 */
    private String tradeNo;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    /** 异常说明（回调金额不一致、模拟支付失败等） */
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
