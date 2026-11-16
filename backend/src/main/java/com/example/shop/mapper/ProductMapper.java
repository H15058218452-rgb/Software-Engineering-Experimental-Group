package com.example.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shop.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductMapper extends BaseMapper<Product> {

    @Select("SELECT * FROM product WHERE id = #{id} FOR UPDATE")
    Product selectByIdForUpdate(@Param("id") Long id);

    /** 基线单商品接口兼容用：取最近创建的一件未下架商品。 */
    @Select("SELECT * FROM product WHERE status IN (0,1,2) ORDER BY created_at DESC, id DESC LIMIT 1 FOR UPDATE")
    Product selectActiveForUpdate();

    /** 基线单商品接口兼容用：取最近创建的一件未下架商品。 */
    @Select("SELECT * FROM product WHERE status IN (0,1,2) ORDER BY created_at DESC, id DESC LIMIT 1")
    Product selectActive();

    /**
     * 成交时原子扣减库存。条件里带 stock > 0，即使漏了行锁也不会扣成负数。
     */
    @Update("UPDATE product SET stock = stock - 1 WHERE id = #{id} AND stock > 0")
    int decrementStock(@Param("id") Long id);

    /** 补货/调整库存（结果为负数时由调用方拦截；这里再用 stock + delta >= 0 兜底）。 */
    @Update("UPDATE product SET stock = stock + #{delta} WHERE id = #{id} AND stock + #{delta} >= 0")
    int addStock(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 下单扣减库存（B.1-1）：条件更新，库存不足或商品非在售时影响行数为 0，由调用方整单回滚。
     * SET 顺序有讲究：前 3 列都基于「扣减前」的 stock 计算，stock 本身必须最后更新，
     * 否则 MySQL 会拿已扣减后的值再减一次，导致归零判断错位。
     */
    @Update("UPDATE product SET "
            + "status = CASE WHEN stock - #{qty} = 0 THEN 3 ELSE status END, "
            + "off_shelf_reason = CASE WHEN stock - #{qty} = 0 THEN 'SOLD_OUT' ELSE off_shelf_reason END, "
            + "off_shelf_at = CASE WHEN stock - #{qty} = 0 THEN NOW(3) ELSE off_shelf_at END, "
            + "stock = stock - #{qty}, "
            + "updated_at = NOW(3) "
            + "WHERE id = #{id} AND stock >= #{qty} AND status = 0")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    /** 取消订单回补库存（B.1-2）：只加数量，不改状态。 */
    @Update("UPDATE product SET stock = stock + #{qty}, updated_at = NOW(3) WHERE id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);

    /** 回补后库存由 0 转正，且商品当初是因库存耗尽下架的，自动恢复在售。 */
    @Update("UPDATE product SET status = 0, off_shelf_reason = NULL, off_shelf_at = NULL, updated_at = NOW(3) "
            + "WHERE id = #{id} AND status = 3 AND off_shelf_reason = 'SOLD_OUT' AND stock > 0")
    int restoreOnSaleIfSoldOut(@Param("id") Long id);
}