package com.example.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shop.entity.AfterSale;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AfterSaleMapper extends BaseMapper<AfterSale> {

    @Select("SELECT * FROM after_sale WHERE after_sale_no = #{afterSaleNo}")
    AfterSale selectByNo(@Param("afterSaleNo") String afterSaleNo);

    @Select("SELECT * FROM after_sale WHERE after_sale_no = #{afterSaleNo} FOR UPDATE")
    AfterSale selectByNoForUpdate(@Param("afterSaleNo") String afterSaleNo);

    /** 当天最大售后单号，用于生成 ASyyyyMMdd-#### 序列。 */
    @Select("SELECT MAX(after_sale_no) FROM after_sale WHERE after_sale_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxNoOfDay(@Param("prefix") String prefix);

    /** 判定「是否有进行中的售后单」，用于 40964 与累计次数校验。 */
    @Select("SELECT COUNT(*) FROM after_sale WHERE order_item_id = #{orderItemId}")
    long countByItem(@Param("orderItemId") Long orderItemId);

    @Select("SELECT COUNT(*) FROM after_sale WHERE order_item_id = #{orderItemId} "
            + "AND status IN ('PENDING', 'WAIT_RETURN')")
    long countActiveByItem(@Param("orderItemId") Long orderItemId);

    /** 状态 CAS：只有当前状态等于 from 时才推进，重复提交自然失败。 */
    @Update("UPDATE after_sale SET status = #{to}, updated_at = NOW(3) "
            + "WHERE after_sale_no = #{afterSaleNo} AND status = #{from}")
    int casStatus(@Param("afterSaleNo") String afterSaleNo,
                  @Param("from") String from,
                  @Param("to") String to);

    /** 同意退货：PENDING → WAIT_RETURN，同时把处理方式定格为退货退款。 */
    @Update("UPDATE after_sale SET status = 'WAIT_RETURN', type = 'RETURN_REFUND', updated_at = NOW(3) "
            + "WHERE after_sale_no = #{afterSaleNo} AND status = 'PENDING'")
    int casAgreeReturn(@Param("afterSaleNo") String afterSaleNo);

    /** 仅退款：PENDING → REFUNDED，记录退款金额与渠道。 */
    @Update("UPDATE after_sale SET status = 'REFUNDED', type = 'REFUND_ONLY', "
            + "refund_amount = #{refundAmount}, refund_channel = #{refundChannel}, refunded_at = NOW(3), "
            + "updated_at = NOW(3) WHERE after_sale_no = #{afterSaleNo} AND status = 'PENDING'")
    int casRefundOnly(@Param("afterSaleNo") String afterSaleNo,
                      @Param("refundAmount") java.math.BigDecimal refundAmount,
                      @Param("refundChannel") String refundChannel);

    /** 确认收到退货并退款：WAIT_RETURN → REFUNDED。 */
    @Update("UPDATE after_sale SET status = 'REFUNDED', refund_amount = #{refundAmount}, "
            + "refund_channel = #{refundChannel}, refunded_at = NOW(3), updated_at = NOW(3) "
            + "WHERE after_sale_no = #{afterSaleNo} AND status = 'WAIT_RETURN'")
    int casReceiveRefund(@Param("afterSaleNo") String afterSaleNo,
                         @Param("refundAmount") java.math.BigDecimal refundAmount,
                         @Param("refundChannel") String refundChannel);
}
