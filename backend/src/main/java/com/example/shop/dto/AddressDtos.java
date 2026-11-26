package com.example.shop.dto;

import java.util.List;

/**
 * 收货地址 DTO（挑战需求 C2-1）。
 *
 * 字段与《接口契约-挑战需求C》一致：receiver / phone / region / detail；
 * 列表返回 data.list，新增/修改返回完整地址对象，设默认只回 addressId + isDefault。
 */
public interface AddressDtos {

    /** 新增/修改入参；修改时字段全部可选，传了才覆盖。 */
    record SaveRequest(String receiver, String phone, String region, String detail,
                       Boolean isDefault) {
    }

    record AddressView(String addressId, String receiver, String phone,
                       String region, String detail, Boolean isDefault) {
    }

    /** GET /api/address 的 data：{ list: [...] } */
    record ListView(List<AddressView> list) {
    }

    /** PUT /api/address/{id}/default 的 data：{ addressId, isDefault } */
    record DefaultView(String addressId, Boolean isDefault) {
    }
}
