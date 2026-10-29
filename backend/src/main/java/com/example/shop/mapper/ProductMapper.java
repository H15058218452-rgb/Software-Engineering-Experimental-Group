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
}