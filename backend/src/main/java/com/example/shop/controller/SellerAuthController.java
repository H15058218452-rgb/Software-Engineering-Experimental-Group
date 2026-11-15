package com.example.shop.controller;

import com.example.shop.auth.SellerAuthInterceptor;
import com.example.shop.common.ApiResponse;
import com.example.shop.dto.CommonDtos;
import com.example.shop.service.SellerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller")
public class SellerAuthController {

    private final SellerService sellerService;

    public SellerAuthController(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    @PostMapping("/login")
    public ApiResponse<CommonDtos.LoginResponse> login(@Valid @RequestBody CommonDtos.LoginRequest req) {
        return ApiResponse.ok(sellerService.login(req));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestAttribute(SellerAuthInterceptor.ATTR_SELLER_ID) Long sellerId,
                                            @Valid @RequestBody CommonDtos.ChangePasswordRequest req) {
        sellerService.changePassword(sellerId, req);
        return ApiResponse.ok();
    }
}