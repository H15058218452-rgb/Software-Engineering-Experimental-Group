package com.example.shop.controller;

import com.example.shop.auth.JwtUtil;
import com.example.shop.common.ApiResponse;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.AddressDtos;
import com.example.shop.service.AddressService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 买家收货地址接口（挑战需求 C2-1 #22647）。
 * 路径按《迭代 4 契约》为 /api/address，与前端 AddressList.vue 对齐；
 * 全部要求登录态，身份取自 Authorization 头中的买家 token。
 */
@RestController
@RequestMapping("/api/address")
public class AddressController {

    private final AddressService addressService;
    private final JwtUtil jwtUtil;

    public AddressController(AddressService addressService, JwtUtil jwtUtil) {
        this.addressService = addressService;
        this.jwtUtil = jwtUtil;
    }

    /** 地址列表：默认地址置顶。 */
    @GetMapping
    public ApiResponse<List<AddressDtos.AddressView>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(addressService.list(requireBuyerId(authorization)));
    }

    /** 新增地址；第一条自动设为默认。 */
    @PostMapping
    public ApiResponse<AddressDtos.AddressView> create(
            @RequestBody AddressDtos.SaveRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(addressService.create(requireBuyerId(authorization), request));
    }

    /** 修改地址；isDefault=true 时同步切换默认地址。 */
    @PutMapping("/{id}")
    public ApiResponse<AddressDtos.AddressView> update(
            @PathVariable Long id,
            @RequestBody AddressDtos.SaveRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(addressService.update(requireBuyerId(authorization), id, request));
    }

    /** 删除地址；若删的是默认地址，自动提升最近一条为默认。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        addressService.delete(requireBuyerId(authorization), id);
        return ApiResponse.ok();
    }

    /** 设置默认地址。 */
    @PutMapping("/{id}/default")
    public ApiResponse<AddressDtos.AddressView> setDefault(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(addressService.setDefault(requireBuyerId(authorization), id));
    }

    private Long requireBuyerId(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            Long buyerId = jwtUtil.parse(authorization.substring(7));
            if (buyerId != null) {
                return buyerId;
            }
        }
        throw new BizException(ErrorCode.UNAUTHORIZED);
    }
}
