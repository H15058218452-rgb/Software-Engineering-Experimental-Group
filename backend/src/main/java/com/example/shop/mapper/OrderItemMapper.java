package com.example.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shop.entity.OrderItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("SELECT * FROM order_item WHERE order_id = #{orderId} ORDER BY id")
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);

    /** 售后按商品粒度定位明细：同一订单同一商品只会有一条（下单时已合并）。 */
    @Select("SELECT * FROM order_item WHERE order_id = #{orderId} AND product_id = #{productId} LIMIT 1")
    OrderItem selectByOrderIdAndProductId(@Param("orderId") Long orderId,
                                          @Param("productId") Long productId);
}
