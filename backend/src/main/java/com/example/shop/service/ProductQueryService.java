package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.common.TimeUtil;
import com.example.shop.dto.CommonDtos;
import com.example.shop.dto.ProductDtos;
import com.example.shop.entity.Category;
import com.example.shop.entity.Intent;
import com.example.shop.entity.Product;
import com.example.shop.enums.IntentStatus;
import com.example.shop.enums.ProductStatus;
import com.example.shop.mapper.IntentMapper;
import com.example.shop.mapper.ProductMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 商品查询服务（列表、搜索、详情、视图转换）。
 *
 * 与写操作分离：{@link ProductService} 负责状态机与库存，本类负责读模型，
 * 避免一个类过大，也方便前端按「读接口 / 写接口」分别对接。
 */
@Service
public class ProductQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ProductMapper productMapper;
    private final IntentMapper intentMapper;
    private final CategoryService categoryService;
    private final ObjectMapper objectMapper;

    public ProductQueryService(ProductMapper productMapper, IntentMapper intentMapper,
                               CategoryService categoryService, ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.intentMapper = intentMapper;
        this.categoryService = categoryService;
        this.objectMapper = objectMapper;
    }

    /** 卖家商品管理列表（含已下架）。 */
    public CommonDtos.PageResult<ProductDtos.ProductListItem> pageSellerProducts(
            Integer status, Long categoryId, String keyword, int page, int pageSize) {
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<>();
        if (status != null) {
            q.eq(Product::getStatus, status);
        }
        appendCategoryFilter(q, categoryId);
        appendKeywordFilter(q, keyword);
        q.orderByDesc(Product::getCreatedAt).orderByDesc(Product::getId);
        return page(q, page, pageSize);
    }

    /** 买家商品列表：只在售且有库存（A.1-4）。 */
    public CommonDtos.PageResult<ProductDtos.ProductListItem> pageBuyerProducts(
            Long categoryId, String keyword, int page, int pageSize) {
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductStatus.ON_SALE.getCode())
                .gt(Product::getStock, 0);
        appendCategoryFilter(q, categoryId);
        appendKeywordFilter(q, keyword);
        q.orderByDesc(Product::getCreatedAt).orderByDesc(Product::getId);
        return page(q, page, pageSize);
    }

    /** 已下架商品历史（基线功能，只读）。 */
    public CommonDtos.PageResult<ProductDtos.ProductView> pageHistory(int page, int pageSize) {
        Page<Product> result = productMapper.selectPage(new Page<>(page, normalizePageSize(pageSize)),
                new LambdaQueryWrapper<Product>().eq(Product::getStatus, ProductStatus.OFF_SHELF.getCode())
                        .orderByDesc(Product::getOffShelfAt).orderByDesc(Product::getId));
        List<ProductDtos.ProductView> list = result.getRecords().stream().map(this::toProductView).toList();
        return new CommonDtos.PageResult<>(page, pageSize, result.getTotal(), list);
    }

    public ProductDtos.ProductView getBuyerProduct(Long id) {
        return toProductView(requireProduct(id));
    }

    public ProductDtos.SellerProductView getSellerProduct(Long id) {
        return toSellerProductView(requireProduct(id));
    }

    /** 基线单商品接口兼容：最近一件未下架商品（买家视角）。 */
    public ProductDtos.ProductView getBuyerCurrent() {
        Product p = productMapper.selectActive();
        return p == null ? null : toProductView(p);
    }

    /** 基线单商品接口兼容：最近一件未下架商品（卖家视角）。 */
    public ProductDtos.SellerProductView getSellerCurrent() {
        Product p = productMapper.selectActive();
        return p == null ? null : toSellerProductView(p);
    }

    public List<ProductDtos.IntentView> listIntents(Long productId) {
        return intentMapper.selectList(new LambdaQueryWrapper<Intent>()
                        .eq(Intent::getProductId, productId)
                        .orderByAsc(Intent::getQueuedAt).orderByAsc(Intent::getId))
                .stream().map(it -> toIntentView(it, queuePosition(it))).toList();
    }

    public List<ProductDtos.IntentView> listHistoryIntents(Long productId) {
        return intentMapper.selectList(new LambdaQueryWrapper<Intent>()
                        .eq(Intent::getProductId, productId)
                        .orderByAsc(Intent::getQueuedAt).orderByAsc(Intent::getId))
                .stream().map(it -> toIntentView(it, null)).toList();
    }

    // ============ 视图转换（写操作复用） ============

    public ProductDtos.SellerProductView toSellerProductView(Product p) {
        int s = p.getStatus();
        int activeCount = Math.toIntExact(intentMapper.selectCount(new LambdaQueryWrapper<Intent>()
                .eq(Intent::getProductId, p.getId())
                .in(Intent::getStatus, IntentStatus.QUEUING.getCode(),
                        IntentStatus.TRADING.getCode(), IntentStatus.REQUEUED.getCode())));
        Intent current = p.getCurrentIntentId() != null ? intentMapper.selectById(p.getCurrentIntentId()) : null;
        ProductDtos.IntentView currentView = current != null ? toIntentView(current, 1) : null;
        boolean onSale = s == ProductStatus.ON_SALE.getCode();
        boolean frozen = s == ProductStatus.FROZEN.getCode();
        boolean trading = s == ProductStatus.TRADING.getCode();
        boolean offShelf = s == ProductStatus.OFF_SHELF.getCode();
        int stock = p.getStock() == null ? 0 : p.getStock();
        return new ProductDtos.SellerProductView(
                String.valueOf(p.getId()), p.getName(), p.getDescription(), p.getImageUrl(),
                p.getPrice() == null ? null : p.getPrice().toPlainString(), s, ProductStatus.textOf(s),
                TimeUtil.format(p.getCreatedAt()), TimeUtil.format(p.getOffShelfAt()),
                p.getCurrentIntentId() == null ? null : String.valueOf(p.getCurrentIntentId()),
                onSale,
                frozen,
                onSale && activeCount > 0 && stock > 0,
                onSale || frozen,
                trading,
                currentView,
                activeCount,
                p.getCategoryId() == null ? null : String.valueOf(p.getCategoryId()),
                categoryService.nameOf(p.getCategoryId()),
                stock,
                readImages(p.getImagesJson()),
                offShelf && stock > 0,
                !trading);
    }

    public ProductDtos.ProductView toProductView(Product p) {
        int s = p.getStatus();
        int stock = p.getStock() == null ? 0 : p.getStock();
        return new ProductDtos.ProductView(
                String.valueOf(p.getId()), p.getName(), p.getDescription(), p.getImageUrl(),
                p.getPrice() == null ? null : p.getPrice().toPlainString(), s, ProductStatus.textOf(s),
                TimeUtil.format(p.getCreatedAt()), TimeUtil.format(p.getOffShelfAt()),
                s == ProductStatus.ON_SALE.getCode() && stock > 0,
                p.getCategoryId() == null ? null : String.valueOf(p.getCategoryId()),
                categoryService.nameOf(p.getCategoryId()),
                stock,
                readImages(p.getImagesJson()));
    }

    // ============ 私有辅助 ============

    private CommonDtos.PageResult<ProductDtos.ProductListItem> page(
            LambdaQueryWrapper<Product> q, int page, int pageSize) {
        Page<Product> result = productMapper.selectPage(new Page<>(page, normalizePageSize(pageSize)), q);
        Map<Long, String> names = categoryService.nameMap();
        List<ProductDtos.ProductListItem> list = result.getRecords().stream()
                .map(p -> toListItem(p, names)).toList();
        return new CommonDtos.PageResult<>(page, pageSize, result.getTotal(), list);
    }

    private ProductDtos.ProductListItem toListItem(Product p, Map<Long, String> names) {
        List<String> images = readImages(p.getImagesJson());
        String imageUrl = p.getImageUrl() != null ? p.getImageUrl() : (images.isEmpty() ? null : images.get(0));
        return new ProductDtos.ProductListItem(
                String.valueOf(p.getId()), p.getName(), imageUrl,
                p.getPrice() == null ? null : p.getPrice().toPlainString(),
                p.getStock() == null ? 0 : p.getStock(),
                p.getStatus(), ProductStatus.textOf(p.getStatus()),
                p.getCategoryId() == null ? null : String.valueOf(p.getCategoryId()),
                p.getCategoryId() == null ? null : names.get(p.getCategoryId()),
                TimeUtil.format(p.getCreatedAt()),
                images.size());
    }

    private Product requireProduct(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return p;
    }

    private void appendCategoryFilter(LambdaQueryWrapper<Product> q, Long categoryId) {
        if (categoryId == null) {
            return;
        }
        Category c = categoryService.requireCategory(categoryId);
        if (c.getParentId() == null) {
            List<Long> childIds = categoryService.childIdsOf(c.getId());
            if (childIds.isEmpty()) {
                q.eq(Product::getId, -1L);
            } else {
                q.in(Product::getCategoryId, childIds);
            }
        } else {
            q.eq(Product::getCategoryId, c.getId());
        }
    }

    private void appendKeywordFilter(LambdaQueryWrapper<Product> q, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String kw = keyword.trim();
        if (kw.length() > 50) {
            kw = kw.substring(0, 50);
        }
        String finalKw = kw;
        q.and(w -> w.like(Product::getName, finalKw).or().like(Product::getDescription, finalKw));
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return 10;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private List<String> readImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private Integer queuePosition(Intent it) {
        int s = it.getStatus();
        if (s == IntentStatus.TRADING.getCode()) {
            return 1;
        }
        if (s == IntentStatus.QUEUING.getCode() || s == IntentStatus.REQUEUED.getCode()) {
            return intentMapper.calcQueuePosition(it.getProductId(), it.getQueuedAt(), it.getId());
        }
        return null;
    }

    private ProductDtos.IntentView toIntentView(Intent it, Integer pos) {
        boolean active = it.getStatus() == IntentStatus.QUEUING.getCode()
                || it.getStatus() == IntentStatus.REQUEUED.getCode();
        return new ProductDtos.IntentView(
                String.valueOf(it.getId()),
                it.getName(),
                it.getPhone(),
                it.getStatus(),
                IntentStatus.textOf(it.getStatus()),
                pos,
                TimeUtil.format(it.getQueuedAt()),
                TimeUtil.format(it.getCreatedAt()),
                active,
                active);
    }
}
