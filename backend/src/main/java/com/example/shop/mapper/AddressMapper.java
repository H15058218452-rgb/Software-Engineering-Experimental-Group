package com.example.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shop.entity.Address;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AddressMapper extends BaseMapper<Address> {

    /** 清空该客户所有地址的默认标记，用于设置新默认地址前的前置步骤。 */
    @Update("UPDATE address SET is_default = 0 WHERE customer_id = #{customerId} AND is_default = 1")
    int clearDefault(@Param("customerId") Long customerId);
}
