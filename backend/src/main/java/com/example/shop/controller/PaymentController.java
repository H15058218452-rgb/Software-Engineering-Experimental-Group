package com.example.shop.controller;

import com.example.shop.auth.JwtUtil;
import com.example.shop.common.ApiResponse;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.PaymentDtos;
import com.example.shop.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口（迭代 4 · C.1-1 契约）。
 *
 * POST /api/order/{orderNo}/pay   买家发起支付
 * GET  /api/order/{orderNo}/pay   买家查询支付结果（收银台轮询）
 * POST /api/pay/callback          Mock 网关回调，内部接口，不校验 token
 *
 * 回调在生产环境必须校验网关签名；MVP 用 Mock，仅做状态幂等。
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final JwtUtil jwtUtil;

    public PaymentController(PaymentService paymentService, JwtUtil jwtUtil) {
        this.paymentService = paymentService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/api/order/{orderNo}/pay")
    public ApiResponse<PaymentDtos.PayView> pay(
            @PathVariable String orderNo,
            @RequestBody(required = false) PaymentDtos.PayRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long buyerId = requireBuyer(authorization);
        return ApiResponse.ok(paymentService.createPay(orderNo, buyerId,
                request == null ? null : request.channel()));
    }

    @GetMapping("/api/order/{orderNo}/pay")
    public ApiResponse<PaymentDtos.PayQueryView> query(
            @PathVariable String orderNo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long buyerId = requireBuyer(authorization);
        return ApiResponse.ok(paymentService.queryPay(orderNo, buyerId));
    }

    @PostMapping("/api/pay/callback")
    public ApiResponse<PaymentDtos.CallbackView> callback(
            @RequestBody(required = false) PaymentDtos.CallbackRequest request) {
        return ApiResponse.ok(paymentService.callback(request));
    }

    private Long requireBuyer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring("Bearer ".length()).trim();
        Long id = jwtUtil.parse(token);
        if (id == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!JwtUtil.ROLE_BUYER.equals(jwtUtil.parseRole(token))) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return id;
    }
}
