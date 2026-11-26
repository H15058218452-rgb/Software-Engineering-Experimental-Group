package com.example.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shop.entity.Payment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 支付单访问。所有状态变更走 CAS，重复回调只有第一次生效。
 */
public interface PaymentMapper extends BaseMapper<Payment> {

    @Select("SELECT * FROM payment WHERE payment_no = #{paymentNo}")
    Payment selectByNo(@Param("paymentNo") String paymentNo);

    @Select("SELECT * FROM payment WHERE payment_no = #{paymentNo} FOR UPDATE")
    Payment selectByNoForUpdate(@Param("paymentNo") String paymentNo);

    /** 订单最近一张支付单，用于查询支付结果。 */
    @Select("SELECT * FROM payment WHERE order_no = #{orderNo} ORDER BY id DESC LIMIT 1")
    Payment selectLatestByOrderNo(@Param("orderNo") String orderNo);

    /** 订单下仍在等待支付的支付单：重复发起支付直接复用它，不新建。 */
    @Select("SELECT * FROM payment WHERE order_no = #{orderNo} AND status = 'PENDING' "
            + "ORDER BY id DESC LIMIT 1")
    Payment selectPendingByOrderNo(@Param("orderNo") String orderNo);

    /** 订单下已支付成功的支付单，取消订单时原路退款。 */
    @Select("SELECT * FROM payment WHERE order_no = #{orderNo} AND status = 'SUCCESS' "
            + "ORDER BY id DESC LIMIT 1")
    Payment selectSuccessByOrderNo(@Param("orderNo") String orderNo);

    /** 当天最大支付单号，用于生成 PAYyyyyMMddHHmmss### 序列。 */
    @Select("SELECT MAX(payment_no) FROM payment WHERE payment_no LIKE CONCAT('PAY', #{day}, '%')")
    String selectMaxNoOfDay(@Param("day") String day);

    @Update("UPDATE payment SET status = 'SUCCESS', trade_no = #{tradeNo}, paid_at = NOW(3), "
            + "updated_at = NOW(3) WHERE payment_no = #{paymentNo} AND status = 'PENDING'")
    int casSuccess(@Param("paymentNo") String paymentNo, @Param("tradeNo") String tradeNo);

    @Update("UPDATE payment SET status = 'ABNORMAL', remark = #{remark}, updated_at = NOW(3) "
            + "WHERE payment_no = #{paymentNo} AND status = 'PENDING'")
    int casAbnormal(@Param("paymentNo") String paymentNo, @Param("remark") String remark);

    @Update("UPDATE payment SET status = 'REFUNDED', refunded_at = NOW(3), updated_at = NOW(3) "
            + "WHERE payment_no = #{paymentNo} AND status = 'SUCCESS'")
    int casRefund(@Param("paymentNo") String paymentNo);

    /**
     * 补偿：回调已把支付单置成功，但订单已关闭（超时取消）导致 15→18 失败，
     * 把支付单退回异常态，避免出现「支付成功但订单已取消」的悬空单。
     */
    @Update("UPDATE payment SET status = 'ABNORMAL', remark = #{remark}, trade_no = NULL, "
            + "paid_at = NULL, updated_at = NOW(3) WHERE payment_no = #{paymentNo} AND status = 'SUCCESS'")
    int revertSuccess(@Param("paymentNo") String paymentNo, @Param("remark") String remark);
}
