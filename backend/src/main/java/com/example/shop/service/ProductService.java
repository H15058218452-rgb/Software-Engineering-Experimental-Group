package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.common.HtmlSanitizer;
import com.example.shop.dto.ProductDtos;
import com.example.shop.entity.Intent;
import com.example.shop.entity.Product;
import com.example.shop.enums.Disposition;
import com.example.shop.enums.ProductStatus;
import com.example.shop.mapper.IntentMapper;
import com.example.shop.mapper.ProductMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 商品写操作服务（迭代 2 · 挑战需求 A）。
 *
 * 与基线的差异：
 * 1. 多商品：去掉「同一时刻仅一件在售」限制；
 * 2. 类目：商品必须挂在二级类别下；
 * 3. 库存：成交扣减，归零自动下架；手动增减不得为负；补货与上架分开；
 * 4. 富媒体：描述为富文本 HTML（白名单清洗），图库最多 9 张，首张为主图。
 *
 * 并发一致性：所有写操作都在事务内 SELECT ... FOR UPDATE 锁商品行。
 */
@Service
public class ProductService {

    private static final int MAX_IMAGES = 9;

    private final ProductMapper productMapper;
    private final IntentMapper intentMapper;
    private final CategoryService categoryService;
    private final ProductQueryService queryService;
    private final ObjectMapper objectMapper;

    public ProductService(ProductMapper productMapper, IntentMapper intentMapper,
                          CategoryService categoryService, ProductQueryService queryService,
                          ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.intentMapper = intentMapper;
        this.categoryService = categoryService;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    // ============ 商品维护 ============

    /** 发布商品：可同时发布多件，不再做单活跃商品校验。 */
    @Transactional
    public ProductDtos.SellerProductView publish(ProductDtos.PublishRequest req) {
        categoryService.requireLeafCategory(req.categoryId());
        List<String> images = normalizeImages(req.images());
        int stock = req.stock() == null ? 1 : req.stock();

        Product p = new Product();
        p.setName(req.name().trim());
        p.setCategoryId(req.categoryId());
        p.setDescription(HtmlSanitizer.sanitize(req.description()));
        p.setImagesJson(writeImages(images));
        p.setImageUrl(images.isEmpty() ? null : images.get(0));
        p.setPrice(req.price());
        p.setStock(stock);
        p.setStatus(ProductStatus.ON_SALE.getCode());
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        productMapper.insert(p);
        return queryService.toSellerProductView(reload(p.getId()));
    }

    /** 编辑商品。库存不走这里，走补货/库存调整接口，避免绕过扣减规则。 */
    @Transactional
    public ProductDtos.SellerProductView update(ProductDtos.UpdateRequest req) {
        Product p = lock(req.productId());
        if (p.getStatus() == ProductStatus.TRADING.getCode()) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT.getCode(),
                    "交易中的商品不可编辑，请先完成或终止当前交易");
        }
        categoryService.requireLeafCategory(req.categoryId());
        List<String> images = normalizeImages(req.images());
        String description = HtmlSanitizer.sanitize(req.description());

        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, p.getId())
                .set(Product::getName, req.name().trim())
                .set(Product::getCategoryId, req.categoryId())
                .set(Product::getDescription, description)
                .set(Product::getImagesJson, writeImages(images))
                .set(Product::getImageUrl, images.isEmpty() ? null : images.get(0))
                .set(Product::getPrice, req.price())
                .set(Product::getUpdatedAt, LocalDateTime.now()));
        return queryService.toSellerProductView(reload(p.getId()));
    }

    /** 手动下架。交易中不可下架；下架时队列其余意向置为「最终失败」，不复活。 */
    @Transactional
    public ProductDtos.SellerProductView offShelf(ProductDtos.ProductActionRequest req) {
        Product p = lock(req.productId());
        if (p.getStatus() == ProductStatus.TRADING.getCode()) {
            throw new BizException(ErrorCode.TRADING_CANNOT_OFF_SHELF);
        }
        if (p.getStatus() == ProductStatus.OFF_SHELF.getCode()) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF);
        }
        intentMapper.closeOthersFinalFailed(p.getId(), -1L);
        updateProductState(p.getId(), ProductStatus.OFF_SHELF.getCode(), null, LocalDateTime.now());
        return queryService.toSellerProductView(reload(p.getId()));
    }

    /** 重新上架：仅对已下架且有库存的商品有效（A.1-3：补货后手动重新上架）。 */
    @Transactional
    public ProductDtos.SellerProductView shelfOn(ProductDtos.ProductActionRequest req) {
        Product p = lock(req.productId());
        if (p.getStatus() != ProductStatus.OFF_SHELF.getCode()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "只有已下架的商品才需要重新上架");
        }
        if (p.getStock() == null || p.getStock() <= 0) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH.getCode(), "库存为 0，请先补货再上架");
        }
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, p.getId())
                .set(Product::getStatus, ProductStatus.ON_SALE.getCode())
                .set(Product::getCurrentIntentId, null)
                .set(Product::getOffShelfAt, null)
                .set(Product::getUpdatedAt, LocalDateTime.now()));
        return queryService.toSellerProductView(reload(p.getId()));
    }

    // ============ 库存 ============

    /** 补货：只增加库存，不改状态；已下架的商品补货后仍需手动上架。 */
    @Transactional
    public ProductDtos.SellerProductView restock(ProductDtos.RestockRequest req) {
        Product p = lock(req.productId());
        if (productMapper.addStock(p.getId(), req.quantity()) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return queryService.toSellerProductView(reload(p.getId()));
    }

    /** 手动调整库存：调整后不得为负；减到 0 自动下架；交易中不可调减。 */
    @Transactional
    public ProductDtos.SellerProductView adjustStock(ProductDtos.StockAdjustRequest req) {
        if (req.delta() == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "调整数量不能为 0");
        }
        Product p = lock(req.productId());
        if (p.getStatus() == ProductStatus.TRADING.getCode() && req.delta() < 0) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT.getCode(), "交易中不可调减库存");
        }
        if (productMapper.addStock(p.getId(), req.delta()) != 1) {
            throw new BizException(ErrorCode.STOCK_BELOW_ZERO);
        }
        Product latest = reload(p.getId());
        if (latest.getStock() != null && latest.getStock() == 0
                && latest.getStatus() == ProductStatus.ON_SALE.getCode()) {
            intentMapper.closeOthersFinalFailed(latest.getId(), -1L);
            updateProductState(latest.getId(), ProductStatus.OFF_SHELF.getCode(), null, LocalDateTime.now());
            latest = reload(latest.getId());
        }
        return queryService.toSellerProductView(latest);
    }

    // ============ 状态机 / 交易 ============

    @Transactional
    public ProductDtos.SellerProductView freeze(ProductDtos.ProductActionRequest req) {
        Product p = lock(req.productId());
        if (p.getStatus() == ProductStatus.OFF_SHELF.getCode()) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF);
        }
        if (p.getStatus() != ProductStatus.ON_SALE.getCode()) {
            String msg = p.getStatus() == ProductStatus.TRADING.getCode() ? "交易中商品不可手动冻结" : "商品已冻结";
            throw new BizException(ErrorCode.PRODUCT_FROZEN.getCode(), msg);
        }
        updateProductState(p.getId(), ProductStatus.FROZEN.getCode(), null, null);
        return queryService.toSellerProductView(reload(p.getId()));
    }

    @Transactional
    public ProductDtos.SellerProductView unfreeze(ProductDtos.ProductActionRequest req) {
        Product p = lock(req.productId());
        if (p.getStatus() == ProductStatus.TRADING.getCode()) {
            throw new BizException(ErrorCode.TRADING_CANNOT_UNFREEZE);
        }
        if (p.getStatus() == ProductStatus.OFF_SHELF.getCode()) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF);
        }
        if (p.getStatus() != ProductStatus.FROZEN.getCode()) {
            throw new BizException(ErrorCode.PRODUCT_FROZEN.getCode(), "商品不在手动冻结状态");
        }
        updateProductState(p.getId(), ProductStatus.ON_SALE.getCode(), null, null);
        return queryService.toSellerProductView(reload(p.getId()));
    }

    @Transactional
    public ProductDtos.SellerProductView startTrade(ProductDtos.StartTradeRequest req) {
        Product p = lock(req.productId());
        if (p.getStatus() == ProductStatus.OFF_SHELF.getCode()) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF);
        }
        if (p.getStatus() != ProductStatus.ON_SALE.getCode()) {
            throw new BizException(ErrorCode.PRODUCT_FROZEN.getCode(), "商品已冻结/交易中，无法开始交易");
        }
        if (p.getStock() == null || p.getStock() <= 0) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH.getCode(), "库存为 0，无法开始交易");
        }
        Intent head = intentMapper.selectFirstInQueue(p.getId());
        if (head == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "当前没有排队买家，无法开始交易");
        }
        if (intentMapper.markTrading(head.getId()) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
        }
        updateProductState(p.getId(), ProductStatus.TRADING.getCode(), head.getId(), null);
        return queryService.toSellerProductView(reload(p.getId()));
    }

    /** 交易成功：扣 1 件库存；仍有库存回到在售，扣到 0 自动下架且队列其余意向失效。 */
    @Transactional
    public ProductDtos.SellerProductView confirmSold(ProductDtos.ConfirmSoldRequest req) {
        Product p = lock(req.productId());
        checkTrading(p, req.intentId());
        if (intentMapper.markSuccess(req.intentId()) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
        }
        if (productMapper.decrementStock(p.getId()) != 1) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        Product latest = reload(p.getId());
        if (latest.getStock() == null || latest.getStock() <= 0) {
            intentMapper.closeOthersFinalFailed(p.getId(), req.intentId());
            updateProductState(p.getId(), ProductStatus.OFF_SHELF.getCode(), null, LocalDateTime.now());
        } else {
            updateProductState(p.getId(), ProductStatus.ON_SALE.getCode(), null, null);
        }
        return queryService.toSellerProductView(reload(p.getId()));
    }

    @Transactional
    public ProductDtos.SellerProductView confirmFailed(ProductDtos.ConfirmFailedRequest req) {
        Product p = lock(req.productId());
        checkTrading(p, req.intentId());
        Disposition disposition = Disposition.from(req.disposition());
        if (disposition == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "disposition 必须为 VOID 或 REQUEUE");
        }
        if (disposition == Disposition.VOID) {
            if (intentMapper.markVoided(req.intentId()) != 1) {
                throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
            }
        } else {
            if (intentMapper.markRequeued(req.intentId(), LocalDateTime.now()) != 1) {
                throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
            }
        }
        Intent next = intentMapper.selectFirstInQueueExclude(p.getId(), req.intentId());
        if (next != null) {
            if (intentMapper.markTrading(next.getId()) != 1) {
                throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
            }
            updateProductState(p.getId(), ProductStatus.TRADING.getCode(), next.getId(), null);
        } else {
            updateProductState(p.getId(), ProductStatus.ON_SALE.getCode(), null, null);
        }
        return queryService.toSellerProductView(reload(p.getId()));
    }

    // ============ 私有辅助 ============

    private Product lock(Long id) {
        Product p = productMapper.selectByIdForUpdate(id);
        if (p == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return p;
    }

    private Product reload(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return p;
    }

    private void checkTrading(Product p, Long intentId) {
        if (p.getStatus() == ProductStatus.OFF_SHELF.getCode()) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF);
        }
        if (p.getStatus() != ProductStatus.TRADING.getCode()) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT.getCode(), "商品未处于交易中，请刷新重试");
        }
        if (p.getCurrentIntentId() == null || !p.getCurrentIntentId().equals(intentId)) {
            throw new BizException(ErrorCode.NOT_QUEUE_HEAD);
        }
    }

    /** 显式更新状态：updateById 不更新 null，必须用 UpdateWrapper 才能置空 current_intent_id。 */
    private void updateProductState(Long id, int status, Long currentIntentId, LocalDateTime offShelfAt) {
        LambdaUpdateWrapper<Product> uw = new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(Product::getStatus, status)
                .set(Product::getCurrentIntentId, currentIntentId)
                .set(Product::getUpdatedAt, LocalDateTime.now());
        if (offShelfAt != null) {
            uw.set(Product::getOffShelfAt, offShelfAt);
        }
        productMapper.update(null, uw);
    }

    /** 图库校验：≤9 张、地址合法、去重，首张为主图。 */
    private List<String> normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String raw : images) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String url = raw.trim();
            if (url.length() > 500) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "图片地址过长");
            }
            if (!(url.startsWith("/uploads/") || url.startsWith("http://") || url.startsWith("https://"))) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "图片地址不合法：" + url);
            }
            set.add(url);
        }
        if (set.size() > MAX_IMAGES) {
            throw new BizException(ErrorCode.IMAGES_TOO_MANY);
        }
        return new ArrayList<>(set);
    }

    private String writeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "图库序列化失败");
        }
    }
}
