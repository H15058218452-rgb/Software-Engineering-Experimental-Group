package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.CartDtos;
import com.example.shop.entity.Cart;
import com.example.shop.entity.Favorite;
import com.example.shop.entity.Product;
import com.example.shop.enums.ProductStatus;
import com.example.shop.mapper.CartMapper;
import com.example.shop.mapper.FavoriteMapper;
import com.example.shop.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 购物车服务（需求 B2-1）：
 * 加入购物车、修改数量/勾选、删除项、获取列表；
 * 另外提供「勾选商品转收藏」与「结算前多商品库存校验」两个能力。
 */
@Service
public class CartService {

    private final CartMapper cartMapper;
    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    public CartService(CartMapper cartMapper, FavoriteMapper favoriteMapper, ProductMapper productMapper) {
        this.cartMapper = cartMapper;
        this.favoriteMapper = favoriteMapper;
        this.productMapper = productMapper;
    }

    /** 加入购物车：同一商品已存在时累加数量，累加后不得超过库存 */
    @Transactional
    public CartDtos.CartItem add(Long buyerId, CartDtos.AddRequest req) {
        Product p = requirePurchasable(req.productId());
        int addQuantity = req.quantity() == null ? 1 : req.quantity();
        Cart cart = findByBuyerAndProduct(buyerId, req.productId());
        int target = (cart == null ? 0 : cart.getQuantity()) + addQuantity;
        if (target > p.getStock()) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH.getCode(),
                    "库存不足，当前库存 " + p.getStock());
        }
        if (cart == null) {
            cart = new Cart();
            cart.setBuyerId(buyerId);
            cart.setProductId(req.productId());
            cart.setQuantity(target);
            cart.setSelected(1);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());
            cartMapper.insert(cart);
        } else {
            cart.setQuantity(target);
            cart.setUpdatedAt(LocalDateTime.now());
            cartMapper.updateById(cart);
        }
        return toItem(cart, p);
    }

    /** 修改数量或勾选状态，至少要传一项 */
    @Transactional
    public CartDtos.CartItem update(Long buyerId, Long productId, CartDtos.UpdateRequest req) {
        if (req.quantity() == null && req.selected() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "数量和勾选状态至少传一项");
        }
        Cart cart = findByBuyerAndProduct(buyerId, productId);
        if (cart == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "购物车中没有该商品");
        }
        Product p = productMapper.selectById(productId);
        if (req.quantity() != null) {
            if (p == null || p.getStock() == null || req.quantity() > p.getStock()) {
                throw new BizException(ErrorCode.STOCK_NOT_ENOUGH.getCode(),
                        "库存不足，当前库存 " + (p == null || p.getStock() == null ? 0 : p.getStock()));
            }
            cart.setQuantity(req.quantity());
        }
        if (req.selected() != null) {
            cart.setSelected(req.selected() == 0 ? 0 : 1);
        }
        cart.setUpdatedAt(LocalDateTime.now());
        cartMapper.updateById(cart);
        return toItem(cart, p);
    }

    @Transactional
    public void remove(Long buyerId, Long productId) {
        int rows = cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getBuyerId, buyerId)
                .eq(Cart::getProductId, productId));
        if (rows == 0) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "购物车中没有该商品");
        }
    }

    /** 购物车列表：合计金额只统计已勾选项 */
    public CartDtos.CartListResponse list(Long buyerId) {
        List<Cart> rows = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getBuyerId, buyerId)
                .orderByDesc(Cart::getCreatedAt)
                .orderByDesc(Cart::getId));
        Map<Long, Product> products = loadProducts(rows);
        List<CartDtos.CartItem> items = rows.stream()
                .map(c -> toItem(c, products.get(c.getProductId())))
                .toList();
        int totalQuantity = items.stream().mapToInt(i -> i.quantity() == null ? 0 : i.quantity()).sum();
        int selectedCount = 0;
        BigDecimal selectedAmount = BigDecimal.ZERO;
        for (CartDtos.CartItem i : items) {
            if (Boolean.TRUE.equals(i.selected())) {
                selectedCount++;
                if (i.subtotal() != null) {
                    selectedAmount = selectedAmount.add(i.subtotal());
                }
            }
        }
        return new CartDtos.CartListResponse(items, items.size(), totalQuantity, selectedAmount, selectedCount);
    }

    /**
     * 购物车转收藏：传 productIds 时按传入的转，未传时取已勾选项。
     * 已在收藏夹中的计入 skipped，成功转入的从购物车移除，计入 moved。
     */
    @Transactional
    public CartDtos.ToFavoriteResponse toFavorites(Long buyerId, CartDtos.ToFavoriteRequest req) {
        List<Cart> rows = resolveRows(buyerId, req == null ? null : req.productIds(), true);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "没有可转为收藏的商品");
        }
        int moved = 0;
        int skipped = 0;
        for (Cart row : rows) {
            Favorite exist = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getBuyerId, buyerId)
                    .eq(Favorite::getProductId, row.getProductId()));
            if (exist != null) {
                skipped++;
            } else {
                Favorite f = new Favorite();
                f.setBuyerId(buyerId);
                f.setProductId(row.getProductId());
                f.setCreatedAt(LocalDateTime.now());
                favoriteMapper.insert(f);
                moved++;
            }
            cartMapper.deleteById(row.getId());
        }
        return new CartDtos.ToFavoriteResponse(moved, skipped);
    }

    /** 结算前校验：逐项检查商品是否在售且库存充足 */
    public CartDtos.CheckoutCheckResponse checkoutCheck(Long buyerId, CartDtos.CheckoutCheckRequest req) {
        List<Cart> rows = resolveRows(buyerId, req == null ? null : req.productIds(), false);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "购物车为空，无法结算");
        }
        Map<Long, Product> products = loadProducts(rows);
        List<CartDtos.CheckoutCheckItem> items = new ArrayList<>();
        boolean allEnough = true;
        for (Cart row : rows) {
            Product p = products.get(row.getProductId());
            String reason = null;
            if (p == null) {
                reason = "商品不存在";
            } else if (p.getStatus() == null || p.getStatus() != ProductStatus.ON_SALE.getCode()) {
                reason = "商品已下架或冻结";
            } else if (p.getStock() == null || p.getStock() < row.getQuantity()) {
                reason = "库存不足，当前库存 " + (p.getStock() == null ? 0 : p.getStock());
            }
            boolean enough = reason == null;
            if (!enough) {
                allEnough = false;
            }
            items.add(new CartDtos.CheckoutCheckItem(
                    row.getProductId(),
                    p == null ? null : p.getName(),
                    row.getQuantity(),
                    p == null ? null : p.getStock(),
                    enough,
                    reason));
        }
        return new CartDtos.CheckoutCheckResponse(allEnough, items);
    }

    /**
     * 解析待处理行：传了 productIds 就按传入的取，否则取全部/已勾选项。
     * onlySelected=true 时未传 productIds 只取勾选项。
     */
    private List<Cart> resolveRows(Long buyerId, List<Long> productIds, boolean onlySelected) {
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<Cart>().eq(Cart::getBuyerId, buyerId);
        if (productIds != null && !productIds.isEmpty()) {
            wrapper.in(Cart::getProductId, productIds);
        } else if (onlySelected) {
            wrapper.eq(Cart::getSelected, 1);
        }
        return cartMapper.selectList(wrapper);
    }

    private Cart findByBuyerAndProduct(Long buyerId, Long productId) {
        return cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getBuyerId, buyerId)
                .eq(Cart::getProductId, productId));
    }

    /** 加入购物车前校验商品：存在、在售、有库存 */
    private Product requirePurchasable(Long productId) {
        Product p = productMapper.selectById(productId);
        if (p == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "商品不存在");
        }
        if (p.getStatus() == null || p.getStatus() != ProductStatus.ON_SALE.getCode()) {
            int code = p.getStatus() != null && p.getStatus() == ProductStatus.OFF_SHELF.getCode()
                    ? ErrorCode.PRODUCT_OFF_SHELF.getCode()
                    : ErrorCode.PRODUCT_FROZEN.getCode();
            throw new BizException(code, "商品已下架或冻结，无法加入购物车");
        }
        if (p.getStock() == null || p.getStock() <= 0) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH.getCode(), "商品库存不足");
        }
        return p;
    }

    private Map<Long, Product> loadProducts(List<Cart> rows) {
        List<Long> ids = rows.stream()
                .map(Cart::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
    }

    private CartDtos.CartItem toItem(Cart c, Product p) {
        Integer status = p == null ? null : p.getStatus();
        boolean available = status != null && status == ProductStatus.ON_SALE.getCode()
                && p.getStock() != null && p.getStock() > 0;
        BigDecimal price = p == null ? null : p.getPrice();
        BigDecimal subtotal = price == null || c.getQuantity() == null
                ? null
                : price.multiply(BigDecimal.valueOf(c.getQuantity()));
        return new CartDtos.CartItem(
                c.getId(),
                c.getProductId(),
                p == null ? null : p.getName(),
                p == null ? null : p.getImageUrl(),
                price,
                p == null ? null : p.getStock(),
                c.getQuantity(),
                c.getSelected() != null && c.getSelected() == 1,
                subtotal,
                status,
                ProductStatus.textOf(status),
                available);
    }
}
