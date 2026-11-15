package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.dto.CommonDtos;
import com.example.shop.dto.ProductDtos;
import com.example.shop.service.ProductQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 卖家后台查询接口（需鉴权）。
 * 迭代 2 新增：多商品分页列表、单个商品详情。
 */
@RestController
@RequestMapping("/api/seller")
public class SellerViewController {

    private final ProductQueryService queryService;

    public SellerViewController(ProductQueryService queryService) {
        this.queryService = queryService;
    }

    /** 基线单商品接口（兼容保留）：最近一件未下架商品。 */
    @GetMapping("/product/current")
    public ApiResponse<ProductDtos.SellerProductView> current() {
        return ApiResponse.ok(queryService.getSellerCurrent());
    }

    /**
     * 卖家商品管理列表（A.1-1）：含在售/冻结/交易中/已下架，可按状态、类别、关键词筛选。
     * status 不传表示全部。
     */
    @GetMapping("/products")
    public ApiResponse<CommonDtos.PageResult<ProductDtos.ProductListItem>> products(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(queryService.pageSellerProducts(status, categoryId, keyword, page, pageSize));
    }

    /** 单个商品详情（管理页编辑用，含图库与富文本正文）。 */
    @GetMapping("/products/{id}")
    public ApiResponse<ProductDtos.SellerProductView> product(@PathVariable Long id) {
        return ApiResponse.ok(queryService.getSellerProduct(id));
    }

    @GetMapping("/intents")
    public ApiResponse<List<ProductDtos.IntentView>> intents(@RequestParam Long productId) {
        return ApiResponse.ok(queryService.listIntents(productId));
    }

    @GetMapping("/history/products")
    public ApiResponse<CommonDtos.PageResult<ProductDtos.ProductView>> historyProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(queryService.pageHistory(page, pageSize));
    }

    @GetMapping("/history/products/{id}/intents")
    public ApiResponse<List<ProductDtos.IntentView>> historyIntents(@PathVariable Long id) {
        return ApiResponse.ok(queryService.listHistoryIntents(id));
    }
}
