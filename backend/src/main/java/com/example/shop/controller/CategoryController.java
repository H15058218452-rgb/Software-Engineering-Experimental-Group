package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.common.IdempotencyService;
import com.example.shop.dto.CategoryDtos;
import com.example.shop.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 两级类别接口。
 * - GET  /api/category/tree   公开（买家筛选、卖家发布页都要读）
 * - POST /api/seller/category、PUT/DELETE /api/seller/category/{id} 需卖家鉴权
 */
@RestController
public class CategoryController {

    private final CategoryService categoryService;
    private final IdempotencyService idempotencyService;

    public CategoryController(CategoryService categoryService, IdempotencyService idempotencyService) {
        this.categoryService = categoryService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/api/category/tree")
    public ApiResponse<List<CategoryDtos.CategoryNode>> tree() {
        return ApiResponse.ok(categoryService.tree());
    }

    @PostMapping("/api/seller/category")
    public ApiResponse<CategoryDtos.CategoryNode> create(@Valid @RequestBody CategoryDtos.SaveCategoryRequest req) {
        return idempotencyService.execute(key("create", req.requestId()),
                () -> ApiResponse.ok(categoryService.create(req)));
    }

    @PutMapping("/api/seller/category/{id}")
    public ApiResponse<CategoryDtos.CategoryNode> update(@PathVariable Long id,
                                                        @Valid @RequestBody CategoryDtos.UpdateCategoryRequest req) {
        return idempotencyService.execute(key("update:" + id, req.requestId()),
                () -> ApiResponse.ok(categoryService.update(id, req)));
    }

    @DeleteMapping("/api/seller/category/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.ok();
    }

    private String key(String op, String requestId) {
        return (requestId == null || requestId.isBlank()) ? null : "category:" + op + ":" + requestId;
    }
}
