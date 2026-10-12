package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.common.TimeUtil;
import com.example.shop.dto.CommonDtos;
import com.example.shop.dto.ProductDtos;
import com.example.shop.entity.Intent;
import com.example.shop.entity.Product;
import com.example.shop.enums.Disposition;
import com.example.shop.enums.IntentStatus;
import com.example.shop.enums.ProductStatus;
import com.example.shop.mapper.IntentMapper;
import com.example.shop.mapper.ProductMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品模块核心服务：发布 / 冻结 / 解冻 / 开始交易 / 交易成功 / 交易失败递补 / 手动下架。
 * 所有写操作均在事务内通过 SELECT ... FOR UPDATE 锁定商品行保证并发一致性。
 */
@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final IntentMapper intentMapper;

    public ProductService(ProductMapper productMapper, IntentMapper intentMapper) {
        this.productMapper = productMapper;
        this.intentMapper = intentMapper;
    }

    // ============ 写操作 ============

    @Transactional
    public ProductDtos.SellerProductView publish(ProductDtos.PublishRequest req) {
        Long activeCount = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .in(Product::getStatus, ProductStatus.ON_SALE.getCode(),
                        ProductStatus.FROZEN.getCode(), ProductStatus.TRADING.getCode()));
        if (activeCount > 0) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT.getCode(), "已有未下架商品，请先下架后再发布");
        }
        Product p = new Product();
        p.setName(req.name().trim());
        p.setDescription(req.description());
        if (req.imageUrl() != null && !req.imageUrl().isBlank()) {
            p.setImageUrl(req.imageUrl().trim());
        }
        p.setPrice(req.price());
        p.setStatus(ProductStatus.ON_SALE.getCode());
        p.setCreatedAt(LocalDateTime.now());
        try {
            productMapper.insert(p);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT.getCode(), "已有未下架商品，请先下架后再发布");
        }
        return getSellerCurrent();
    }

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
        return getSellerCurrent();
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
        return getSellerCurrent();
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
        Intent head = intentMapper.selectFirstInQueue(p.getId());
        if (head == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "当前没有排队买家，无法开始交易");
        }
        if (intentMapper.markTrading(head.getId()) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
        }
        updateProductState(p.getId(), ProductStatus.TRADING.getCode(), head.getId(), null);
        return getSellerCurrent();
    }

    @Transactional
    public ProductDtos.SellerProductView confirmSold(ProductDtos.ConfirmSoldRequest req) {
        Product p = lock(req.productId());
        checkTrading(p, req.intentId());
        if (intentMapper.markSuccess(req.intentId()) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
        }
        intentMapper.closeOthersFinalFailed(p.getId(), req.intentId());
        updateProductState(p.getId(), ProductStatus.OFF_SHELF.getCode(), null, LocalDateTime.now());
        return getSellerCurrent();
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
        return getSellerCurrent();
    }

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
        return getSellerCurrent();
    }

    // ============ 查询操作 ============

    public ProductDtos.SellerProductView getSellerCurrent() {
        Product p = productMapper.selectActive();
        return p == null ? null : toSellerProductView(p);
    }

    public ProductDtos.ProductView getBuyerCurrent() {
        Product p = productMapper.selectActive();
        return p == null ? null : toProductView(p);
    }

    public List<ProductDtos.IntentView> listIntents(Long productId) {
        List<Intent> list = intentMapper.selectList(new LambdaQueryWrapper<Intent>()
                .eq(Intent::getProductId, productId)
                .orderByAsc(Intent::getQueuedAt).orderByAsc(Intent::getId));
        return list.stream().map(it -> toIntentView(it, position(it))).toList();
    }

    public List<ProductDtos.IntentView> listHistoryIntents(Long productId) {
        List<Intent> list = intentMapper.selectList(new LambdaQueryWrapper<Intent>()
                .eq(Intent::getProductId, productId)
                .orderByAsc(Intent::getQueuedAt).orderByAsc(Intent::getId));
        return list.stream().map(it -> toIntentView(it, null)).toList();
    }

    public CommonDtos.PageResult<ProductDtos.ProductView> pageHistory(int page, int pageSize) {
        Page<Product> result = productMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<Product>().eq(Product::getStatus, ProductStatus.OFF_SHELF.getCode())
                        .orderByDesc(Product::getOffShelfAt).orderByDesc(Product::getId));
        List<ProductDtos.ProductView> list = result.getRecords().stream().map(this::toProductView).toList();
        return new CommonDtos.PageResult<>(page, pageSize, result.getTotal(), list);
    }

    // ============ 私有辅助 ============

    private Product lock(Long id) {
        Product p = productMapper.selectByIdForUpdate(id);
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

    /**
     * 显式更新商品状态与 current_intent_id。
     * 注意：updateById 默认策略不更新 null 字段，必须用 UpdateWrapper 才能把 current_intent_id 置空。
     */
    private void updateProductState(Long id, int status, Long currentIntentId, LocalDateTime offShelfAt) {
        LambdaUpdateWrapper<Product> uw = new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(Product::getStatus, status)
                .set(Product::getCurrentIntentId, currentIntentId);
        if (offShelfAt != null) {
            uw.set(Product::getOffShelfAt, offShelfAt);
        }
        productMapper.update(null, uw);
    }

    private Integer position(Intent it) {
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

    private ProductDtos.ProductView toProductView(Product p) {
        return new ProductDtos.ProductView(
                String.valueOf(p.getId()), p.getName(), p.getDescription(), p.getImageUrl(),
                p.getPrice().toPlainString(), p.getStatus(), ProductStatus.textOf(p.getStatus()),
                TimeUtil.format(p.getCreatedAt()), TimeUtil.format(p.getOffShelfAt()),
                p.getStatus() == ProductStatus.ON_SALE.getCode());
    }

    private ProductDtos.SellerProductView toSellerProductView(Product p) {
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
        return new ProductDtos.SellerProductView(
                String.valueOf(p.getId()), p.getName(), p.getDescription(), p.getImageUrl(),
                p.getPrice().toPlainString(), s, ProductStatus.textOf(s),
                TimeUtil.format(p.getCreatedAt()), TimeUtil.format(p.getOffShelfAt()),
                p.getCurrentIntentId() == null ? null : String.valueOf(p.getCurrentIntentId()),
                onSale,
                frozen,
                onSale && activeCount > 0,
                onSale || frozen,
                trading,
                currentView,
                activeCount);
    }
}
