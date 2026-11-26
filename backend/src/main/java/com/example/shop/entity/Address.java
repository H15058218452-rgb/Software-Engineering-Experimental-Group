package com.example.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 买家收货地址（挑战需求 C2-1）。
 *
 * 字段与《接口契约-挑战需求C》一致：customer_id / receiver / phone / region / detail。
 * 与 buyer.address（默认交易地点）区分：这里是地址簿，可维护多条并指定唯一默认地址。
 */
@TableName("address")
public class Address {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String receiver;
    private String phone;
    /** 所在地区，整串存储，如：浙江省杭州市西湖区 */
    private String region;
    private String detail;
    /** 1 默认地址；同一客户最多一条，由 AddressService 保证 */
    private Integer isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Integer getIsDefault() { return isDefault; }
    public void setIsDefault(Integer isDefault) { this.isDefault = isDefault; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
