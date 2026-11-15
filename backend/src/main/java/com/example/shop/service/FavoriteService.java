package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.common.TimeUtil;
import com.example.shop.dto.CommonDtos;
import com.example.shop.dto.FavoriteDtos;
import com.example.shop.entity.Favorite;
import com.example.shop.entity.Product;
import com.example.shop.enums.ProductStatus;
import com.example.shop.mapper.FavoriteMapper;
import com.example.shop.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 收藏夹服务（需求 B2-2）：添加收藏、取消收藏、收藏列表。
 * 同一买家对同一商品只保留一条记录，重复添加视为幂等成功。
 */
@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    public FavoriteService(FavoriteMapper favoriteMapper, ProductMapper productMapper) {
        this.favoriteMapper = favoriteMapper;
        this.productMapper = productMapper;
    }

    @Transactional
    public FavoriteDtos.FavoriteItem add(Long buyerId, Long productId) {
        Product p = productMapper.selectById(productId);
        if (p == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "商品不存在");
        }
        Favorite exist = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getBuyerId, buyerId)
                .eq(Favorite::getProductId, productId));
        if (exist != null) {
            return toItem(exist, p);
        }
        Favorite f = new Favorite();
        f.setBuyerId(buyerId);
        f.setProductId(productId);
        f.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(f);
        return toItem(f, p);
    }

    @Transactional
    public void remove(Long buyerId, Long productId) {
        int rows = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getBuyerId, buyerId)
                .eq(Favorite::getProductId, productId));
        if (rows == 0) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "未收藏该商品");
        }
    }

    public CommonDtos.PageResult<FavoriteDtos.FavoriteItem> list(Long buyerId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getBuyerId, buyerId)
                .orderByDesc(Favorite::getCreatedAt)
                .orderByDesc(Favorite::getId);
        Page<Favorite> result = favoriteMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        Map<Long, Product> products = loadProducts(result.getRecords());
        List<FavoriteDtos.FavoriteItem> items = result.getRecords().stream()
                .map(f -> toItem(f, products.get(f.getProductId())))
                .toList();
        return new CommonDtos.PageResult<>(safePage, safeSize, result.getTotal(), items);
    }

    private Map<Long, Product> loadProducts(List<Favorite> records) {
        List<Long> ids = records.stream()
                .map(Favorite::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
    }

    private FavoriteDtos.FavoriteItem toItem(Favorite f, Product p) {
        Integer status = p == null ? null : p.getStatus();
        boolean available = status != null && status == ProductStatus.ON_SALE.getCode()
                && p.getStock() != null && p.getStock() > 0;
        return new FavoriteDtos.FavoriteItem(
                f.getId(),
                f.getProductId(),
                p == null ? null : p.getName(),
                p == null ? null : p.getImageUrl(),
                p == null ? null : p.getPrice(),
                status,
                ProductStatus.textOf(status),
                available,
                TimeUtil.format(f.getCreatedAt()));
    }
}
