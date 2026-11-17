package com.example.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shop.entity.OrderItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("SELECT * FROM order_item WHERE order_id = #{orderId} ORDER BY id")
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);
}
