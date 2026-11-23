package com.example.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shop.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 订单主表访问。
 * 状态变更一律走 CAS（UPDATE ... WHERE status = 期望值），避免并发下重复推进。
 */
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM `order` WHERE order_no = #{orderNo}")
    Order selectByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM `order` WHERE order_no = #{orderNo} FOR UPDATE")
    Order selectByOrderNoForUpdate(@Param("orderNo") String orderNo);

    /**
     * 当天最大订单号，用于生成 yyyyMMdd-#### 的流水号。
     * 外层事务已锁商品行，并发下单在同一商品上串行；跨商品并发由 uk_order_no 兜底。
     */
    @Select("SELECT order_no FROM `order` WHERE order_no LIKE CONCAT(#{prefix}, '-%') "
            + "ORDER BY order_no DESC LIMIT 1")
    String selectMaxOrderNoOfDay(@Param("prefix") String prefix);

    /** 状态 CAS：只有当前状态等于 from 时才更新，返回影响行数。 */
    @Update("UPDATE `order` SET status = #{to}, updated_at = NOW(3) "
            + "WHERE order_no = #{orderNo} AND status = #{from}")
    int casStatus(@Param("orderNo") String orderNo,
                  @Param("from") int from,
                  @Param("to") int to);

    /**
     * 发货 CAS（挑战需求 C2-2）：仅当订单处于“备货完成”时写入物流单号并推进到“已发货”。
     * 单号与状态同一条语句写入，避免出现“已发货但没有单号”的中间态。
     */
    @Update("UPDATE `order` SET status = #{to}, tracking_no = #{trackingNo}, carrier = #{carrier}, "
            + "shipped_at = NOW(3), updated_at = NOW(3) "
            + "WHERE order_no = #{orderNo} AND status = #{from}")
    int shipWithTracking(@Param("orderNo") String orderNo,
                         @Param("from") int from,
                         @Param("to") int to,
                         @Param("trackingNo") String trackingNo,
                         @Param("carrier") String carrier);

    /** 按物流单号反查订单，供轨迹查询接口使用。 */
    @Select("SELECT * FROM `order` WHERE tracking_no = #{trackingNo}")
    Order selectByTrackingNo(@Param("trackingNo") String trackingNo);
}
