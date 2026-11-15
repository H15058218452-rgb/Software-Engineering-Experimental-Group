package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.dto.CommonDtos;
import com.example.shop.dto.SellerCustomerDtos;
import com.example.shop.service.SellerCustomerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 卖家客户管理接口（A.2-3，需卖家鉴权，由拦截器统一处理）。
 */
@RestController
@RequestMapping("/api/seller/customers")
public class SellerCustomerController {

    private final SellerCustomerService customerService;

    public SellerCustomerController(SellerCustomerService customerService) {
        this.customerService = customerService;
    }

    /** 所有注册客户列表：注册时间倒序，可按手机号/昵称搜索。 */
    @GetMapping
    public ApiResponse<CommonDtos.PageResult<SellerCustomerDtos.CustomerView>> customers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(customerService.pageCustomers(keyword, page, pageSize));
    }

    /** 指定客户的购买历史（其手机号提交过的全部意向，时间倒序）。 */
    @GetMapping("/{id}/intents")
    public ApiResponse<List<SellerCustomerDtos.CustomerIntentView>> customerIntents(@PathVariable Long id) {
        return ApiResponse.ok(customerService.listCustomerIntents(id));
    }
}
