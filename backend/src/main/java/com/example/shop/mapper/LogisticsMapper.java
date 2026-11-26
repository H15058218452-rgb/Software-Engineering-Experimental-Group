package com.example.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shop.entity.Logistics;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LogisticsMapper extends BaseMapper<Logistics> {

    /** 一订单一条物流记录，按订单号取唯一一条。 */
    @Select("SELECT * FROM logistics WHERE order_no = #{orderNo} LIMIT 1")
    Logistics selectByOrderNo(@Param("orderNo") String orderNo);
}
