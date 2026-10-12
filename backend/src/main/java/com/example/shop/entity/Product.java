package com.example.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体（active_flag 为数据库生成列，不映射到实体）。
 */
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    /** 0在售 1手动冻结 2交易中冻结 3已下架 */
    private Integer status;
    private Long currentIntentId;
    private LocalDateTime createdAt;
    private LocalDateTime offShelfAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getCurrentIntentId() { return currentIntentId; }
    public void setCurrentIntentId(Long currentIntentId) { this.currentIntentId = currentIntentId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getOffShelfAt() { return offShelfAt; }
    public void setOffShelfAt(LocalDateTime offShelfAt) { this.offShelfAt = offShelfAt; }
}
