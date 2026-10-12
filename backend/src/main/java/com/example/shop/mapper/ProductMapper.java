package com.example.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shop.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductMapper extends BaseMapper<Product> {

    @Select("SELECT * FROM product WHERE id = #{id} FOR UPDATE")
    Product selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM product WHERE status IN (0,1,2) ORDER BY created_at DESC, id DESC LIMIT 1 FOR UPDATE")
    Product selectActiveForUpdate();

    @Select("SELECT * FROM product WHERE status IN (0,1,2) ORDER BY created_at DESC, id DESC LIMIT 1")
    Product selectActive();
}