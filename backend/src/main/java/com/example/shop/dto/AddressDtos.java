package com.example.shop.dto;

/**
 * 收货地址 DTO（挑战需求 C2-1）。ID 用字符串，与其他域保持一致。
 */
public interface AddressDtos {

    /** 新增/修改入参；修改时不传的字段按 null 处理为“不修改”。 */
    record SaveRequest(String receiverName, String receiverPhone,
                       String province, String city, String district, String detail,
                       Boolean isDefault) {
    }

    record AddressView(String id, String receiverName, String receiverPhone,
                       String province, String city, String district, String detail,
                       String fullAddress, Boolean isDefault) {
    }
}
