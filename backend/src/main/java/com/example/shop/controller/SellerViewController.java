package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.dto.CommonDtos;
import com.example.shop.dto.ProductDtos;
import com.example.shop.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/seller")
public class SellerViewController {

    private final ProductService productService;

    public SellerViewController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/product/current")
    public ApiResponse<ProductDtos.SellerProductView> current() {
        return ApiResponse.ok(productService.getSellerCurrent());
    }

    @GetMapping("/intents")
    public ApiResponse<List<ProductDtos.IntentView>> intents(@RequestParam Long productId) {
        return ApiResponse.ok(productService.listIntents(productId));
    }

    @GetMapping("/history/products")
    public ApiResponse<CommonDtos.PageResult<ProductDtos.ProductView>> historyProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(productService.pageHistory(page, pageSize));
    }

    @GetMapping("/history/products/{id}/intents")
    public ApiResponse<List<ProductDtos.IntentView>> historyIntents(@PathVariable Long id) {
        return ApiResponse.ok(productService.listHistoryIntents(id));
    }
}