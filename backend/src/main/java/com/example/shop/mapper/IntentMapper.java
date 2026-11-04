package com.example.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shop.entity.Intent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface IntentMapper extends BaseMapper<Intent> {

    @Select("SELECT * FROM intent WHERE product_id = #{productId} AND status IN (0,5) ORDER BY queued_at ASC, id ASC LIMIT 1")
    Intent selectFirstInQueue(@Param("productId") Long productId);

    @Select("SELECT * FROM intent WHERE product_id = #{productId} AND id <> #{excludeId} AND status IN (0,5) ORDER BY queued_at ASC, id ASC LIMIT 1")
    Intent selectFirstInQueueExclude(@Param("productId") Long productId, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM intent WHERE product_id = #{productId} AND phone = #{phone} AND status IN (0,1,5)")
    int countActiveByPhone(@Param("productId") Long productId, @Param("phone") String phone);

    @Select("SELECT COUNT(*) + 1 FROM intent WHERE product_id = #{productId} AND status IN (0,5) AND (queued_at < #{queuedAt} OR (queued_at = #{queuedAt} AND id < #{id}))")
    int calcQueuePosition(@Param("productId") Long productId, @Param("queuedAt") LocalDateTime queuedAt, @Param("id") Long id);

    @Update("UPDATE intent SET status = 1 WHERE id = #{id} AND status IN (0,5)")
    int markTrading(@Param("id") Long id);

    @Update("UPDATE intent SET status = 2 WHERE id = #{id} AND status = 1")
    int markSuccess(@Param("id") Long id);

    @Update("UPDATE intent SET status = 3 WHERE id = #{id} AND status = 1")
    int markVoided(@Param("id") Long id);

    @Update("UPDATE intent SET status = 5, queued_at = #{now}, fail_count = fail_count + 1 WHERE id = #{id} AND status = 1")
    int markRequeued(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE intent SET status = 4 WHERE id = #{id} AND status IN (0,5)")
    int markCancelled(@Param("id") Long id);

    @Update("UPDATE intent SET status = 6 WHERE product_id = #{productId} AND status IN (0,1,5) AND id <> #{excludeId}")
    int closeOthersFinalFailed(@Param("productId") Long productId, @Param("excludeId") Long excludeId);
}