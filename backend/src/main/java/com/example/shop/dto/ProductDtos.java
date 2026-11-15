package com.example.shop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public interface ProductDtos {

    /**
     * 发布商品（迭代 2：可同时发布多件，不再限制单活跃商品）。
     * images 为商品图库，最多 9 张，第一张作为主图。
     */
    record PublishRequest(
            @NotBlank(message = "商品名称不能为空") @Size(max = 255, message = "商品名称不能超过255字") String name,
            @NotNull(message = "请选择二级类别") Long categoryId,
            @Size(max = 200000, message = "富文本描述过长") String description,
            @NotNull(message = "价格不能为空")
            @DecimalMin(value = "0.01", message = "价格必须大于0")
            @Digits(integer = 8, fraction = 2, message = "价格最多8位整数2位小数") BigDecimal price,
            @Min(value = 1, message = "发布时库存至少为1") Integer stock,
            List<String> images,
            String requestId) {
    }

    /** 编辑商品（可改名称/描述/图集/价格/类别，库存走库存接口单独调整）。 */
    record UpdateRequest(
            @NotNull(message = "商品ID不能为空") Long productId,
            @NotBlank(message = "商品名称不能为空") @Size(max = 255, message = "商品名称不能超过255字") String name,
            @NotNull(message = "请选择二级类别") Long categoryId,
            @Size(max = 200000, message = "富文本描述过长") String description,
            @NotNull(message = "价格不能为空")
            @DecimalMin(value = "0.01", message = "价格必须大于0")
            @Digits(integer = 8, fraction = 2, message = "价格最多8位整数2位小数") BigDecimal price,
            List<String> images,
            String requestId) {
    }

    record ProductActionRequest(@NotNull(message = "商品ID不能为空") Long productId, String requestId) {
    }

    /** 补货：库存增加 quantity；若商品已因售罄下架，补货后自动回到在售状态。 */
    record RestockRequest(
            @NotNull(message = "商品ID不能为空") Long productId,
            @NotNull(message = "补货数量不能为空") @Min(value = 1, message = "补货数量至少为1") Integer quantity,
            String requestId) {
    }

    /** 手动调整库存：delta 可正可负，调整后不得为负；减到 0 自动下架。 */
    record StockAdjustRequest(
            @NotNull(message = "商品ID不能为空") Long productId,
            @NotNull(message = "调整数量不能为空") Integer delta,
            String requestId) {
    }

    record StartTradeRequest(@NotNull(message = "商品ID不能为空") Long productId, String requestId) {
    }

    record ConfirmSoldRequest(
            @NotNull(message = "商品ID不能为空") Long productId,
            @NotNull(message = "意向ID不能为空") Long intentId,
            String requestId) {
    }

    record ConfirmFailedRequest(
            @NotNull(message = "商品ID不能为空") Long productId,
            @NotNull(message = "意向ID不能为空") Long intentId,
            @NotBlank(message = "处置方式不能为空") String disposition,
            String requestId) {
    }

    record IntentView(String id, String name, String phone, Integer status, String statusText,
                      Integer queuePosition, String queuedAt, String createdAt,
                      Boolean canModify, Boolean canCancel) {
    }

    /** 买家侧商品详情。字段只增不改，兼容基线前端。 */
    record ProductView(String id, String name, String description, String imageUrl, String price,
                       Integer status, String statusText, String createTime, String offShelfTime,
                       Boolean canSubmitIntent,
                       String categoryId, String categoryName, Integer stock, List<String> images) {
    }

    /** 列表项（不含富文本正文，避免列表接口返回大字段）。 */
    record ProductListItem(String id, String name, String imageUrl, String price, Integer stock,
                          Integer status, String statusText, String categoryId, String categoryName,
                          String createTime, Integer imageCount) {
    }

    /** 卖家侧商品详情。字段只增不改，兼容基线前端。 */
    record SellerProductView(String id, String name, String description, String imageUrl, String price,
                             Integer status, String statusText, String createTime, String offShelfTime,
                             String currentIntentId,
                             Boolean canFreeze, Boolean canUnfreeze, Boolean canStartTrade,
                             Boolean canOffShelf, Boolean canConfirmTrade,
                             IntentView currentIntent, Integer intentCount,
                             String categoryId, String categoryName, Integer stock,
                             List<String> images, Boolean canRestock, Boolean canEdit) {
    }
}
