package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.dto.BuyerAuthDtos;
import com.example.shop.service.BuyerAccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家账号接口：注册 / 登录。
 * 注册成功直接返回 token（免二次登录），两者均不需要鉴权。
 */
@RestController
@RequestMapping("/api/buyer")
public class BuyerAuthController {

    private final BuyerAccountService buyerAccountService;

    public BuyerAuthController(BuyerAccountService buyerAccountService) {
        this.buyerAccountService = buyerAccountService;
    }

    @PostMapping("/register")
    public ApiResponse<BuyerAuthDtos.AuthResponse> register(
            @Valid @RequestBody BuyerAuthDtos.RegisterRequest req) {
        return ApiResponse.ok(buyerAccountService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<BuyerAuthDtos.AuthResponse> login(
            @Valid @RequestBody BuyerAuthDtos.LoginRequest req) {
        return ApiResponse.ok(buyerAccountService.login(req));
    }
}
