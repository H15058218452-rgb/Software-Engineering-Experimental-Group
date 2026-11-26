package com.example.shop.dto;

/**
 * 收货地址 DTO（挑战需求 C2-1）。
 *
 * 字段与《迭代 4 契约》一致：receiver / phone / region / detail；
 * 响应中的地址主键字段名为 addressId，前端页面直接使用该字段。
 */
public interface AddressDtos {

    /** 新增/修改入参；修改时字段全部可选，传了才覆盖。 */
    record SaveRequest(String receiver, String phone, String region, String detail,
                       Boolean isDefault) {
    }

    record AddressView(String addressId, String receiver, String phone,
                       String region, String detail, Boolean isDefault) {
    }
}
