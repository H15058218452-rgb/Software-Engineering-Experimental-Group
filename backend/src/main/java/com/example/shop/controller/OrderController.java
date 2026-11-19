package com.example.shop.controller;

import com.example.shop.auth.JwtUtil;
import com.example.shop.common.ApiResponse;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.OrderDtos;
import com.example.shop.enums.OrderAction;
import com.example.shop.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口（契约第三章 9 个端点中的 8 个，卖家按客户查订单见 SellerOrderController）。
 *
 * 鉴权说明：/api/order/** 不在卖家拦截器范围内，角色由 token 的 role 声明判定。
 * 买家 token 由登录接口写入 role=buyer；卖家 token（含迭代 3 之前签发的旧 token）视为卖家。
 */
@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;
    private final JwtUtil jwtUtil;

    public OrderController(OrderService orderService, JwtUtil jwtUtil) {
        this.orderService = orderService;
        this.jwtUtil = jwtUtil;
    }

    /** 下单。前端不直接调用，由结算接口内部调用 OrderService；保留端点便于联调与自测。 */
    @PostMapping
    public ApiResponse<OrderDtos.OrderView> create(
            @RequestBody OrderDtos.CreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Actor actor = requireBuyer(authorization);
        return ApiResponse.ok(orderService.create(actor.id(), request == null ? null : request.items()));
    }

    /** 我的订单：含已取消，按下单时间倒序。 */
    @GetMapping("/my")
    public ApiResponse<OrderDtos.OrderPage> myOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Actor actor = requireBuyer(authorization);
        return ApiResponse.ok(orderService.myOrders(actor.id(), page, size));
    }

    /** 订单详情：买家仅限自己的订单，卖家可看全部。 */
    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDtos.OrderView> detail(
            @PathVariable String orderNo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Actor actor = resolve(authorization);
        return ApiResponse.ok(orderService.detail(orderNo, actor.id(), actor.seller()));
    }

    @PostMapping("/{orderNo}/confirm")
    public ApiResponse<OrderDtos.StatusView> confirm(
            @PathVariable String orderNo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireSeller(authorization);
        return ApiResponse.ok(orderService.advance(orderNo, OrderAction.CONFIRM));
    }

    @PostMapping("/{orderNo}/ready")
    public ApiResponse<OrderDtos.StatusView> ready(
            @PathVariable String orderNo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireSeller(authorization);
        return ApiResponse.ok(orderService.advance(orderNo, OrderAction.READY));
    }

    @PostMapping("/{orderNo}/ship")
    public ApiResponse<OrderDtos.StatusView> ship(
            @PathVariable String orderNo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireSeller(authorization);
        return ApiResponse.ok(orderService.advance(orderNo, OrderAction.SHIP));
    }

    @PostMapping("/{orderNo}/complete")
    public ApiResponse<OrderDtos.StatusView> complete(
            @PathVariable String orderNo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireSeller(authorization);
        return ApiResponse.ok(orderService.advance(orderNo, OrderAction.COMPLETE));
    }

    /** 取消：客户可取消 10/20/30，商家可取消 10/20/30/40。 */
    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderDtos.StatusView> cancel(
            @PathVariable String orderNo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Actor actor = resolve(authorization);
        return ApiResponse.ok(orderService.cancel(orderNo, actor.id(), actor.seller()));
    }

    // ============ 鉴权辅助 ============

    private record Actor(Long id, boolean seller) {
    }

    private Actor resolve(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring("Bearer ".length()).trim();
        Long id = jwtUtil.parse(token);
        if (id == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        boolean seller = !JwtUtil.ROLE_BUYER.equals(jwtUtil.parseRole(token));
        return new Actor(id, seller);
    }

    private Actor requireBuyer(String authorization) {
        Actor actor = resolve(authorization);
        if (actor.seller()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return actor;
    }

    private void requireSeller(String authorization) {
        if (!resolve(authorization).seller()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
