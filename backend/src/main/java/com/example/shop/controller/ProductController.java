package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.common.IdempotencyService;
import com.example.shop.dto.ProductDtos;
import com.example.shop.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品模块接口（卖家后台，全部需鉴权）。
 * 地址与契约文档第二章接口清单保持一致。
 */
@RestController
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;
    private final IdempotencyService idempotencyService;

    public ProductController(ProductService productService, IdempotencyService idempotencyService) {
        this.productService = productService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/publish")
    public ApiResponse<ProductDtos.SellerProductView> publish(@Valid @RequestBody ProductDtos.PublishRequest req) {
        return idempotencyService.execute(key("publish", req.requestId()),
                () -> ApiResponse.ok(productService.publish(req)));
    }

    @PostMapping("/freeze")
    public ApiResponse<ProductDtos.SellerProductView> freeze(@Valid @RequestBody ProductDtos.ProductActionRequest req) {
        return idempotencyService.execute(key("freeze", req.requestId()),
                () -> ApiResponse.ok(productService.freeze(req)));
    }

    @PostMapping("/unfreeze")
    public ApiResponse<ProductDtos.SellerProductView> unfreeze(@Valid @RequestBody ProductDtos.ProductActionRequest req) {
        return idempotencyService.execute(key("unfreeze", req.requestId()),
                () -> ApiResponse.ok(productService.unfreeze(req)));
    }

    @PostMapping("/start-trade")
    public ApiResponse<ProductDtos.SellerProductView> startTrade(@Valid @RequestBody ProductDtos.StartTradeRequest req) {
        return idempotencyService.execute(key("start-trade", req.requestId()),
                () -> ApiResponse.ok(productService.startTrade(req)));
    }

    @PostMapping("/confirm-sold")
    public ApiResponse<ProductDtos.SellerProductView> confirmSold(@Valid @RequestBody ProductDtos.ConfirmSoldRequest req) {
        return idempotencyService.execute(key("confirm-sold", req.requestId()),
                () -> ApiResponse.ok(productService.confirmSold(req)));
    }

    @PostMapping("/confirm-failed")
    public ApiResponse<ProductDtos.SellerProductView> confirmFailed(@Valid @RequestBody ProductDtos.ConfirmFailedRequest req) {
        return idempotencyService.execute(key("confirm-failed", req.requestId()),
                () -> ApiResponse.ok(productService.confirmFailed(req)));
    }

    @PostMapping("/off-shelf")
    public ApiResponse<ProductDtos.SellerProductView> offShelf(@Valid @RequestBody ProductDtos.ProductActionRequest req) {
        return idempotencyService.execute(key("off-shelf", req.requestId()),
                () -> ApiResponse.ok(productService.offShelf(req)));
    }

    /** 编辑商品（A.1-1）：名称/描述/图集/价格/类别，库存走库存接口。 */
    @PostMapping("/update")
    public ApiResponse<ProductDtos.SellerProductView> update(@Valid @RequestBody ProductDtos.UpdateRequest req) {
        return idempotencyService.execute(key("update", req.requestId()),
                () -> ApiResponse.ok(productService.update(req)));
    }

    /** 重新上架：已下架且库存 > 0 的商品手动回到在售（A.1-3）。 */
    @PostMapping("/shelf-on")
    public ApiResponse<ProductDtos.SellerProductView> shelfOn(@Valid @RequestBody ProductDtos.ProductActionRequest req) {
        return idempotencyService.execute(key("shelf-on", req.requestId()),
                () -> ApiResponse.ok(productService.shelfOn(req)));
    }

    /** 补货：库存增加，不改状态（A.1-3）。 */
    @PostMapping("/restock")
    public ApiResponse<ProductDtos.SellerProductView> restock(@Valid @RequestBody ProductDtos.RestockRequest req) {
        return idempotencyService.execute(key("restock", req.requestId()),
                () -> ApiResponse.ok(productService.restock(req)));
    }

    /** 手动调整库存：加减后不得为负，减到 0 自动下架（A.1-3）。 */
    @PostMapping("/stock-adjust")
    public ApiResponse<ProductDtos.SellerProductView> adjustStock(@Valid @RequestBody ProductDtos.StockAdjustRequest req) {
        return idempotencyService.execute(key("stock-adjust", req.requestId()),
                () -> ApiResponse.ok(productService.adjustStock(req)));
    }

    private String key(String op, String requestId) {
        return (requestId == null || requestId.isBlank()) ? null : "product:" + op + ":" + requestId;
    }
}
