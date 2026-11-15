package com.example.shop.controller;

import com.example.shop.auth.JwtUtil;
import com.example.shop.common.ApiResponse;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.CommonDtos;
import com.example.shop.dto.FavoriteDtos;
import com.example.shop.service.FavoriteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家收藏夹接口（需求 B2-2）：添加收藏、取消收藏、收藏列表。全部接口要求登录。
 */
@RestController
@RequestMapping("/api/buyer/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final JwtUtil jwtUtil;

    public FavoriteController(FavoriteService favoriteService, JwtUtil jwtUtil) {
        this.favoriteService = favoriteService;
        this.jwtUtil = jwtUtil;
    }

    /** 添加收藏（重复添加幂等返回已有记录） */
    @PostMapping
    public ApiResponse<FavoriteDtos.FavoriteItem> add(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody FavoriteDtos.AddRequest req) {
        return ApiResponse.ok(favoriteService.add(requireBuyerId(authorization), req.productId()));
    }

    /** 取消收藏 */
    @DeleteMapping("/{productId}")
    public ApiResponse<Void> remove(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long productId) {
        favoriteService.remove(requireBuyerId(authorization), productId);
        return ApiResponse.ok();
    }

    /** 收藏列表（按收藏时间倒序分页） */
    @GetMapping
    public ApiResponse<CommonDtos.PageResult<FavoriteDtos.FavoriteItem>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(favoriteService.list(requireBuyerId(authorization), page, pageSize));
    }

    /** 收藏夹全部接口要求登录，未携带或失效一律 40101 */
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
