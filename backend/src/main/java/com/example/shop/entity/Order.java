package com.example.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表（迭代 3 · 挑战需求 B.1）。
 *
 * 与迭代 2 的差异：意向排队模型整体由订单取代，订单直接对应一次成交，
 * 五态作用于整单，不支持部分确认 / 部分发货 / 部分完成。
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
    /** 10客户下单 20商家确认 30备货完成 40已发货 50交易完成 90已取消 */
    private Integer status;
    /** 物流单号，发货时写入（挑战需求 C2-2） */
    private String trackingNo;
    /** 物流公司编码，目前固定 SF 顺丰 */
    private String carrier;
    /** 发货时间，物流轨迹的起点 */
    private LocalDateTime shippedAt;
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
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public LocalDateTime getShippedAt() { return shippedAt; }
    public void setShippedAt(LocalDateTime shippedAt) { this.shippedAt = shippedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
