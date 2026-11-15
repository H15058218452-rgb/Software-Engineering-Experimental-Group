package com.example.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体。
 *
 * 迭代 2 变更：
 * - 去掉 active_flag 生成列与「单活跃商品」唯一约束，多商品可同时在售；
 * - 新增 stock（库存）、categoryId（二级类别）、imagesJson（商品图库 JSON 数组）；
 * - description 改为富文本 HTML（入库前已做白名单清洗）。
 */
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long categoryId;
    private String description;
    private String imageUrl;
    private String imagesJson;
    private BigDecimal price;
    /** 库存，>=0；成交扣减，归零自动下架 */
    private Integer stock;
    /** 0在售 1手动冻结 2交易中冻结 3已下架 */
    private Integer status;
    private Long currentIntentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime offShelfAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getImagesJson() { return imagesJson; }
    public void setImagesJson(String imagesJson) { this.imagesJson = imagesJson; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getCurrentIntentId() { return currentIntentId; }
    public void setCurrentIntentId(Long currentIntentId) { this.currentIntentId = currentIntentId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getOffShelfAt() { return offShelfAt; }
    public void setOffShelfAt(LocalDateTime offShelfAt) { this.offShelfAt = offShelfAt; }
}
