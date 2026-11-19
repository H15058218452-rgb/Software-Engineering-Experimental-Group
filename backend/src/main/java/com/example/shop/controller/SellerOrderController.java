package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.dto.OrderDtos;
import com.example.shop.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家侧订单查询。挂在 /api/seller/** 下，由 SellerAuthInterceptor 统一鉴权。
 *
 * B.1-4 裁定：商家不设全局订单列表、不按状态筛选，只能从客户详情页进入查看该客户的订单记录。
 */
@RestController
@RequestMapping("/api/seller/customer")
public class SellerOrderController {

    private final OrderService orderService;

    public SellerOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 某客户的订单列表（含已取消），按下单时间倒序。 */
    @GetMapping("/{customerId}/order")
    public ApiResponse<OrderDtos.OrderPage> customerOrders(
            @PathVariable Long customerId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.ok(orderService.customerOrders(customerId, page, size));
    }
}
