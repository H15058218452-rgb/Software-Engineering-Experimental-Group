package com.example.shop.controller;

import com.example.shop.auth.JwtUtil;
import com.example.shop.common.ApiResponse;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.AfterSaleDtos;
import com.example.shop.service.AfterSaleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 售后接口（迭代 4 · C.3-1 买家侧 / C.3-2 商家侧，契约第六节 9 个端点）。
 *
 * 买家：提交 / 我的列表 / 详情 / 撤销
 * 卖家：全部列表 / 详情 / 同意退货 / 确认收到退货并退款 / 仅退款 / 拒绝
 */
@RestController
public class AfterSaleController {

    private final AfterSaleService afterSaleService;
    private final JwtUtil jwtUtil;

    public AfterSaleController(AfterSaleService afterSaleService, JwtUtil jwtUtil) {
        this.afterSaleService = afterSaleService;
        this.jwtUtil = jwtUtil;
    }

    // ============ 买家侧 ============

    @PostMapping("/api/after-sale")
    public ApiResponse<AfterSaleDtos.AfterSaleView> create(
            @RequestBody(required = false) AfterSaleDtos.CreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Actor actor = requireRole(authorization, JwtUtil.ROLE_BUYER);
        return ApiResponse.ok(afterSaleService.create(actor.id(), request));
    }

    @GetMapping("/api/after-sale/my")
    public ApiResponse<AfterSaleDtos.AfterSalePage> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Actor actor = requireRole(authorization, JwtUtil.ROLE_BUYER);
        return ApiResponse.ok(afterSaleService.mine(actor.id(), status, page, size));
    }

    /** 详情：买家只能看自己的，卖家可看全部。 */
    @GetMapping("/api/after-sale/{afterSaleNo}")
    public ApiResponse<AfterSaleDtos.AfterSaleView> detail(
            @PathVariable String afterSaleNo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Actor actor = requireRole(authorization, null);
        boolean seller = JwtUtil.ROLE_SELLER.equals(actor.role());
        return ApiResponse.ok(afterSaleService.detail(afterSaleNo, actor.id(), seller));
    }

    @PostMapping("/api/after-sale/{afterSaleNo}/cancel")
    public ApiResponse<AfterSaleDtos.StatusView> cancel(
            @PathVariable String afterSaleNo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Actor actor = requireRole(authorization, JwtUtil.ROLE_BUYER);
        return ApiResponse.ok(afterSaleService.cancel(afterSaleNo, actor.id()));
    }

    // ============ 卖家侧 ============

    @GetMapping("/api/seller/after-sale")
    public ApiResponse<AfterSaleDtos.AfterSalePage> sellerPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireRole(authorization, JwtUtil.ROLE_SELLER);
        return ApiResponse.ok(afterSaleService.sellerPage(status, page, size));
    }

    @GetMapping("/api/seller/after-sale/{afterSaleNo}")
    public ApiResponse<AfterSaleDtos.AfterSaleView> sellerDetail(
            @PathVariable String afterSaleNo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireRole(authorization, JwtUtil.ROLE_SELLER);
        return ApiResponse.ok(afterSaleService.detail(afterSaleNo, null, true));
    }

    /** 同意退货：待处理 → 待收货。 */
    @PostMapping("/api/seller/after-sale/{afterSaleNo}/agree-return")
    public ApiResponse<AfterSaleDtos.StatusView> agreeReturn(
            @PathVariable String afterSaleNo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireRole(authorization, JwtUtil.ROLE_SELLER);
        return ApiResponse.ok(afterSaleService.agreeReturn(afterSaleNo));
    }

    /** 确认收到退货并退款：待收货 → 已退款，回补库存。 */
    @PostMapping("/api/seller/after-sale/{afterSaleNo}/receive")
    public ApiResponse<AfterSaleDtos.StatusView> receive(
            @PathVariable String afterSaleNo,
            @RequestBody(required = false) AfterSaleDtos.ReceiveRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireRole(authorization, JwtUtil.ROLE_SELLER);
        return ApiResponse.ok(afterSaleService.receive(afterSaleNo));
    }

    /** 仅退款不退货：待处理 → 已退款，不回补库存。 */
    @PostMapping("/api/seller/after-sale/{afterSaleNo}/refund-only")
    public ApiResponse<AfterSaleDtos.StatusView> refundOnly(
            @PathVariable String afterSaleNo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireRole(authorization, JwtUtil.ROLE_SELLER);
        return ApiResponse.ok(afterSaleService.refundOnly(afterSaleNo));
    }

    /** 拒绝售后：待处理 → 已拒绝，理由不强制、不展示给客户。 */
    @PostMapping("/api/seller/after-sale/{afterSaleNo}/reject")
    public ApiResponse<AfterSaleDtos.StatusView> reject(
            @PathVariable String afterSaleNo,
            @RequestBody(required = false) AfterSaleDtos.RejectRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireRole(authorization, JwtUtil.ROLE_SELLER);
        return ApiResponse.ok(afterSaleService.reject(afterSaleNo));
    }

    // ============ 鉴权 ============

    private Actor requireRole(String authorization, String expectedRole) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring("Bearer ".length()).trim();
        Long id = jwtUtil.parse(token);
        String role = jwtUtil.parseRole(token);
        if (id == null || role == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (expectedRole != null && !expectedRole.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return new Actor(id, role);
    }

    private record Actor(Long id, String role) {
    }
}
