package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.common.IdempotencyService;
import com.example.shop.dto.BuyerDtos;
import com.example.shop.dto.CommonDtos;
import com.example.shop.dto.ProductDtos;
import com.example.shop.service.BuyerService;
import com.example.shop.service.ProductQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家端接口（免登录浏览 + 提交意向）。
 * 迭代 2 新增：多商品列表（分页/类别/关键词）与商品详情（图库 + 富文本正文）。
 */
@RestController
@RequestMapping("/api/buyer")
public class BuyerController {

    private final ProductQueryService queryService;
    private final BuyerService buyerService;
    private final IdempotencyService idempotencyService;

    public BuyerController(ProductQueryService queryService, BuyerService buyerService,
                           IdempotencyService idempotencyService) {
        this.queryService = queryService;
        this.buyerService = buyerService;
        this.idempotencyService = idempotencyService;
    }

    /** 基线单商品接口（兼容保留）：最近一件未下架商品。 */
    @GetMapping("/product/current")
    public ApiResponse<ProductDtos.ProductView> current() {
        return ApiResponse.ok(queryService.getBuyerCurrent());
    }

    /**
     * 买家商品列表（A.1-4）：只返回在售且有库存的商品。
     * categoryId 可传一级或二级：传一级时自动包含其下所有二级类别。
     */
    @GetMapping("/products")
    public ApiResponse<CommonDtos.PageResult<ProductDtos.ProductListItem>> products(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(queryService.pageBuyerProducts(categoryId, keyword, page, pageSize));
    }

    /** 买家商品详情（A.1-5）：含图库与图文视频混排的富文本正文。 */
    @GetMapping("/products/{id}")
    public ApiResponse<ProductDtos.ProductView> product(@PathVariable Long id) {
        return ApiResponse.ok(queryService.getBuyerProduct(id));
    }

    @PostMapping("/intent")
    public ApiResponse<BuyerDtos.SubmitIntentResponse> submit(@Valid @RequestBody BuyerDtos.SubmitIntentRequest req) {
        String key = (req.requestId() == null || req.requestId().isBlank()) ? null : "buyer:intent:" + req.requestId();
        return idempotencyService.execute(key, () -> ApiResponse.ok(buyerService.submit(req)));
    }

    @GetMapping("/intent")
    public ApiResponse<BuyerDtos.BuyerIntentView> query(@RequestParam String code) {
        return ApiResponse.ok(buyerService.query(code));
    }

    @PutMapping("/intent")
    public ApiResponse<BuyerDtos.BuyerIntentView> modify(@Valid @RequestBody BuyerDtos.ModifyIntentRequest req) {
        return ApiResponse.ok(buyerService.modify(req));
    }

    @PostMapping("/intent/cancel")
    public ApiResponse<Void> cancel(@Valid @RequestBody BuyerDtos.CancelIntentRequest req) {
        buyerService.cancel(req);
        return ApiResponse.ok();
    }
}
