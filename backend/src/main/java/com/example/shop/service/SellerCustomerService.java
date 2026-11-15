package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.common.TimeUtil;
import com.example.shop.dto.CommonDtos;
import com.example.shop.dto.SellerCustomerDtos;
import com.example.shop.entity.Buyer;
import com.example.shop.entity.Intent;
import com.example.shop.entity.Product;
import com.example.shop.enums.IntentStatus;
import com.example.shop.mapper.BuyerMapper;
import com.example.shop.mapper.IntentMapper;
import com.example.shop.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 卖家客户管理服务（A.2-3）。
 * 客户 = buyer 表注册账号；购买历史 = 该客户手机号提交过的全部意向（含成交记录）。
 */
@Service
public class SellerCustomerService {

    private static final int MAX_PAGE_SIZE = 50;

    private final BuyerMapper buyerMapper;
    private final IntentMapper intentMapper;
    private final ProductMapper productMapper;

    public SellerCustomerService(BuyerMapper buyerMapper, IntentMapper intentMapper, ProductMapper productMapper) {
        this.buyerMapper = buyerMapper;
        this.intentMapper = intentMapper;
        this.productMapper = productMapper;
    }

    /** 客户列表：注册时间倒序，可按用户名/手机号/昵称关键词筛选。 */
    public CommonDtos.PageResult<SellerCustomerDtos.CustomerView> pageCustomers(String keyword, int page, int pageSize) {
        LambdaQueryWrapper<Buyer> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            if (kw.length() > 50) {
                kw = kw.substring(0, 50);
            }
            String finalKw = kw;
            q.and(w -> w.like(Buyer::getUsername, finalKw)
                    .or().like(Buyer::getPhone, finalKw)
                    .or().like(Buyer::getNickname, finalKw));
        }
        q.orderByDesc(Buyer::getCreatedAt).orderByDesc(Buyer::getId);
        int size = pageSize <= 0 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<Buyer> result = buyerMapper.selectPage(new Page<>(page, size), q);
        List<SellerCustomerDtos.CustomerView> list = result.getRecords().stream()
                .map(this::toCustomerView).toList();
        return new CommonDtos.PageResult<>(page, pageSize, result.getTotal(), list);
    }

    /** 指定客户的购买历史：按提交时间倒序，含商品名称与价格快照。 */
    public List<SellerCustomerDtos.CustomerIntentView> listCustomerIntents(Long buyerId) {
        Buyer buyer = buyerMapper.selectById(buyerId);
        if (buyer == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (buyer.getPhone() == null) {
            // 手机号为选填，未填则无法与意向单关联
            return List.of();
        }
        List<Intent> intents = intentMapper.selectList(new LambdaQueryWrapper<Intent>()
                .eq(Intent::getPhone, buyer.getPhone())
                .orderByDesc(Intent::getCreatedAt).orderByDesc(Intent::getId));
        Map<Long, Product> products = intents.stream()
                .map(Intent::getProductId)
                .distinct()
                .map(productMapper::selectById)
                .filter(p -> p != null)
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return intents.stream().map(it -> {
            Product p = products.get(it.getProductId());
            return new SellerCustomerDtos.CustomerIntentView(
                    String.valueOf(it.getId()),
                    it.getProductId() == null ? null : String.valueOf(it.getProductId()),
                    p == null ? null : p.getName(),
                    p == null || p.getPrice() == null ? null : p.getPrice().toPlainString(),
                    it.getStatus(),
                    IntentStatus.textOf(it.getStatus()),
                    TimeUtil.format(it.getQueuedAt()),
                    TimeUtil.format(it.getCreatedAt()));
        }).toList();
    }

    private SellerCustomerDtos.CustomerView toCustomerView(Buyer b) {
        int intentCount = 0;
        int successCount = 0;
        if (b.getPhone() != null) {
            intentCount = Math.toIntExact(intentMapper.selectCount(
                    new LambdaQueryWrapper<Intent>().eq(Intent::getPhone, b.getPhone())));
            successCount = Math.toIntExact(intentMapper.selectCount(
                    new LambdaQueryWrapper<Intent>().eq(Intent::getPhone, b.getPhone())
                            .eq(Intent::getStatus, IntentStatus.SUCCESS.getCode())));
        }
        return new SellerCustomerDtos.CustomerView(
                String.valueOf(b.getId()), b.getUsername(), b.getPhone(), b.getNickname(), b.getAddress(),
                TimeUtil.format(b.getCreatedAt()), intentCount, successCount);
    }
}
