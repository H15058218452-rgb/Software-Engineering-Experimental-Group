package com.example.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表（迭代 4 · 挑战需求 C）。
 *
 * 八态作用于整单：15 待支付 → 18 已支付 → 20 商家确认 → 30 备货完成
 * → 40 已发货 → 50 交易完成，任意非终态可转 90 已取消。
 *
 * order 为 MySQL 保留字，表名统一加反引号。
 */
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 业务订单号 yyyyMMdd-####，对外唯一标识 */
    private String orderNo;
    /** 下单客户 buyer.id */
    private Long customerId;
    private BigDecimal totalAmount;
    /** 10客户下单 15待支付 18已支付 20商家确认 30备货完成 40已发货 50交易完成 90已取消 */
    private Integer status;
    /** 支付截止时间，下单后 24 小时；超时由定时任务自动取消 */
    private LocalDateTime payDeadline;
    /** 支付成功时间 */
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getPayDeadline() { return payDeadline; }
    public void setPayDeadline(LocalDateTime payDeadline) { this.payDeadline = payDeadline; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
