package com.example.shop.controller;

import com.example.shop.auth.JwtUtil;
import com.example.shop.common.ApiResponse;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.CartDtos;
import com.example.shop.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家购物车接口（需求 B2-1）：
 * 加入购物车、修改数量/勾选、删除项、获取列表；
 * 另含「勾选商品转收藏」与「结算前多商品库存校验」。全部接口要求登录。
 */
@RestController
@RequestMapping("/api/buyer/cart")
public class CartController {

    private final CartService cartService;
    private final JwtUtil jwtUtil;

    public CartController(CartService cartService, JwtUtil jwtUtil) {
        this.cartService = cartService;
        this.jwtUtil = jwtUtil;
    }

    /** 加入购物车 */
    @PostMapping
    public ApiResponse<CartDtos.CartItem> add(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CartDtos.AddRequest req) {
        return ApiResponse.ok(cartService.add(requireBuyerId(authorization), req));
    }

    /** 修改数量或勾选状态 */
    @PutMapping("/{productId}")
    public ApiResponse<CartDtos.CartItem> update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long productId,
            @Valid @RequestBody CartDtos.UpdateRequest req) {
        return ApiResponse.ok(cartService.update(requireBuyerId(authorization), productId, req));
    }

    /** 删除购物车项 */
    @DeleteMapping("/{productId}")
    public ApiResponse<Void> remove(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long productId) {
        cartService.remove(requireBuyerId(authorization), productId);
        return ApiResponse.ok();
    }

    /** 购物车列表 */
    @GetMapping
    public ApiResponse<CartDtos.CartListResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(cartService.list(requireBuyerId(authorization)));
    }

    /** 勾选的多件商品转为收藏 */
    @PostMapping("/to-favorites")
    public ApiResponse<CartDtos.ToFavoriteResponse> toFavorites(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) CartDtos.ToFavoriteRequest req) {
        return ApiResponse.ok(cartService.toFavorites(requireBuyerId(authorization), req));
    }

    /** 结算前校验多件商品库存是否充足 */
    @PostMapping("/checkout-check")
    public ApiResponse<CartDtos.CheckoutCheckResponse> checkoutCheck(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) CartDtos.CheckoutCheckRequest req) {
        return ApiResponse.ok(cartService.checkoutCheck(requireBuyerId(authorization), req));
    }

    /** 购物车全部接口要求登录，未携带或失效一律 40101 */
    private Long requireBuyerId(String authorization) {
        Long buyerId = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            buyerId = jwtUtil.parse(authorization.substring(7));
        }
        if (buyerId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return buyerId;
    }
}
