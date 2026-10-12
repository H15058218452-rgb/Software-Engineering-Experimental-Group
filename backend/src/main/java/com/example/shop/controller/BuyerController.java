package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.common.IdempotencyService;
import com.example.shop.dto.BuyerDtos;
import com.example.shop.dto.ProductDtos;
import com.example.shop.service.BuyerService;
import com.example.shop.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/buyer")
public class BuyerController {

    private final ProductService productService;
    private final BuyerService buyerService;
    private final IdempotencyService idempotencyService;

    public BuyerController(ProductService productService, BuyerService buyerService, IdempotencyService idempotencyService) {
        this.productService = productService;
        this.buyerService = buyerService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/product/current")
    public ApiResponse<ProductDtos.ProductView> current() {
        return ApiResponse.ok(productService.getBuyerCurrent());
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