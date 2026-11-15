package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.common.TimeUtil;
import com.example.shop.dto.BuyerDtos;
import com.example.shop.dto.CommonDtos;
import com.example.shop.dto.ProductDtos;
import com.example.shop.entity.Buyer;
import com.example.shop.entity.Intent;
import com.example.shop.entity.Product;
import com.example.shop.enums.IntentStatus;
import com.example.shop.enums.ProductStatus;
import com.example.shop.mapper.BuyerMapper;
import com.example.shop.mapper.IntentMapper;
import com.example.shop.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class BuyerService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProductMapper productMapper;
    private final IntentMapper intentMapper;
    private final BuyerMapper buyerMapper;

    public BuyerService(ProductMapper productMapper, IntentMapper intentMapper, BuyerMapper buyerMapper) {
        this.productMapper = productMapper;
        this.intentMapper = intentMapper;
        this.buyerMapper = buyerMapper;
    }

    @Transactional
    public BuyerDtos.SubmitIntentResponse submit(BuyerDtos.SubmitIntentRequest req, Long buyerId) {
        // 登录态提交：身份以 Token 为准，姓名/电话未填时取买家账号资料（需求 A.2-1 废除口令码身份）
        Buyer buyer = null;
        if (buyerId != null) {
            buyer = buyerMapper.selectById(buyerId);
            if (buyer == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED);
            }
        }
        String name = trimToNull(req.name());
        String phone = trimToNull(req.phone());
        if (buyer != null) {
            if (name == null) {
                String nickname = trimToNull(buyer.getNickname());
                name = nickname != null ? nickname : buyer.getUsername();
            }
            if (phone == null) {
                phone = buyer.getPhone();
            }
        }
        // 无 Token 的旧调用仍要求姓名和电话
        if (buyer == null && (name == null || phone == null)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "姓名和联系电话不能为空");
        }

        // 迭代 2：多商品各自独立队列，必须按 productId 锁定对应商品；
        // 未传 productId 时回退到基线行为（最近一件在售商品），保证旧前端可用。
        Product p;
        if (req.productId() != null) {
            p = productMapper.selectByIdForUpdate(req.productId());
            if (p == null) {
                throw new BizException(ErrorCode.NOT_FOUND.getCode(), "商品不存在");
            }
        } else {
            p = productMapper.selectActiveForUpdate();
            if (p == null) {
                throw new BizException(ErrorCode.NOT_FOUND.getCode(), "暂无在售商品");
            }
        }
        if (p.getStatus() != ProductStatus.ON_SALE.getCode()) {
            throw new BizException(ErrorCode.PRODUCT_FROZEN.getCode(), "商品已冻结/交易中，无法提交意向");
        }
        // 同一买家同一商品仅一条有效意向；手机号允许重复，故不再按手机号跨账号去重
        if (buyerId != null && intentMapper.countActiveByBuyer(p.getId(), buyerId) > 0) {
            throw new BizException(ErrorCode.DUPLICATE_INTENT);
        }
        if (buyerId == null && phone != null && intentMapper.countActiveByPhone(p.getId(), phone) > 0) {
            throw new BizException(ErrorCode.DUPLICATE_INTENT);
        }
        String code = generateCode();
        Intent it = new Intent();
        it.setProductId(p.getId());
        it.setBuyerId(buyerId);
        it.setCodeHash(sha256(code));
        it.setName(name);
        it.setPhone(phone);
        it.setStatus(IntentStatus.QUEUING.getCode());
        it.setQueuedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        it.setFailCount(0);
        it.setCreatedAt(LocalDateTime.now());
        it.setUpdatedAt(LocalDateTime.now());
        intentMapper.insert(it);
        int position = intentMapper.calcQueuePosition(p.getId(), it.getQueuedAt(), it.getId());
        return new BuyerDtos.SubmitIntentResponse(code, toIntentView(it, position));
    }

    /**
     * 我的订单（需求 A.2-2）：按买家账号查询历史下单记录，提交时间倒序、支持翻页。
     * 老数据（buyer_id 为 NULL 的口令码意向）按买家注册手机号回退匹配。
     */
    public CommonDtos.PageResult<BuyerDtos.OrderItem> orders(Long buyerId, int page, int pageSize) {
        Buyer buyer = buyerMapper.selectById(buyerId);
        if (buyer == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);

        LambdaQueryWrapper<Intent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Intent::getBuyerId, buyerId);
        String phone = trimToNull(buyer.getPhone());
        if (phone != null) {
            wrapper.or(w -> w.isNull(Intent::getBuyerId).eq(Intent::getPhone, phone));
        }
        wrapper.orderByDesc(Intent::getCreatedAt).orderByDesc(Intent::getId);

        Page<Intent> result = intentMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<Intent> records = result.getRecords();
        Map<Long, String> productNames = loadProductNames(records);
        List<BuyerDtos.OrderItem> items = records.stream()
                .map(it -> new BuyerDtos.OrderItem(
                        String.valueOf(it.getId()),
                        productNames.get(it.getProductId()),
                        TimeUtil.format(it.getCreatedAt()),
                        it.getStatus(),
                        IntentStatus.textOf(it.getStatus()),
                        dealTimeOf(it)))
                .toList();
        return new CommonDtos.PageResult<>(safePage, safeSize, result.getTotal(), items);
    }

    private Map<Long, String> loadProductNames(List<Intent> records) {
        List<Long> productIds = records.stream()
                .map(Intent::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));
    }

    /** 只有已出结果（成功/作废/最终失败）才展示交易时间 */
    private String dealTimeOf(Intent it) {
        int status = it.getStatus();
        if (status == IntentStatus.SUCCESS.getCode()
                || status == IntentStatus.VOIDED.getCode()
                || status == IntentStatus.FINAL_FAILED.getCode()) {
            return TimeUtil.format(it.getUpdatedAt());
        }
        return null;
    }

    private String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    public BuyerDtos.BuyerIntentView query(String code) {
        Intent it = findByCode(code);
        if (isTerminal(it.getStatus())) {
            throw new BizException(ErrorCode.INVALID_CODE.getCode(), "口令码已失效，无对应意向信息");
        }
        return toBuyerView(it);
    }

    @Transactional
    public BuyerDtos.BuyerIntentView modify(BuyerDtos.ModifyIntentRequest req) {
        Intent it = findByCode(req.code());
        if (isTerminal(it.getStatus())) {
            throw new BizException(ErrorCode.INVALID_CODE.getCode(), "口令码已失效");
        }
        if (it.getStatus() != IntentStatus.QUEUING.getCode() && it.getStatus() != IntentStatus.REQUEUED.getCode()) {
            throw new BizException(ErrorCode.INTENT_STATUS_DENIED.getCode(), "当前状态不允许修改意向信息");
        }
        boolean changed = false;
        if (req.name() != null && !req.name().isBlank()) {
            it.setName(req.name().trim());
            changed = true;
        }
        if (req.phone() != null && !req.phone().isBlank()) {
            it.setPhone(req.phone().trim());
            changed = true;
        }
        if (!changed) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "姓名或电话至少填写一项");
        }
        intentMapper.updateById(it);
        return toBuyerView(it);
    }

    @Transactional
    public void cancel(BuyerDtos.CancelIntentRequest req) {
        Intent it = findByCode(req.code());
        if (it.getStatus() == IntentStatus.TRADING.getCode()) {
            throw new BizException(ErrorCode.TRADING_CANNOT_CANCEL);
        }
        if (isTerminal(it.getStatus())) {
            throw new BizException(ErrorCode.INVALID_CODE.getCode(), "口令码已失效");
        }
        if (it.getStatus() != IntentStatus.QUEUING.getCode() && it.getStatus() != IntentStatus.REQUEUED.getCode()) {
            throw new BizException(ErrorCode.INTENT_STATUS_DENIED);
        }
        if (intentMapper.markCancelled(it.getId()) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
        }
    }

    private BuyerDtos.BuyerIntentView toBuyerView(Intent it) {
        Product p = productMapper.selectById(it.getProductId());
        Integer productStatus = p == null ? null : p.getStatus();
        String productName = p == null ? null : p.getName();
        String productPrice = p == null || p.getPrice() == null ? null : p.getPrice().toPlainString();
        Integer position;
        if (it.getStatus() == IntentStatus.TRADING.getCode()) {
            position = 1;
        } else {
            position = intentMapper.calcQueuePosition(it.getProductId(), it.getQueuedAt(), it.getId());
        }
        boolean active = it.getStatus() == IntentStatus.QUEUING.getCode()
                || it.getStatus() == IntentStatus.REQUEUED.getCode();
        return new BuyerDtos.BuyerIntentView(
                String.valueOf(it.getId()),
                it.getStatus(),
                IntentStatus.textOf(it.getStatus()),
                position,
                active,
                active,
                it.getName(),
                it.getPhone(),
                TimeUtil.format(it.getQueuedAt()),
                productStatus,
                ProductStatus.textOf(productStatus),
                productName,
                productPrice,
                TimeUtil.format(it.getCreatedAt()));
    }

    private ProductDtos.IntentView toIntentView(Intent it, int position) {
        return new ProductDtos.IntentView(
                String.valueOf(it.getId()),
                it.getName(),
                it.getPhone(),
                it.getStatus(),
                IntentStatus.textOf(it.getStatus()),
                position,
                TimeUtil.format(it.getQueuedAt()),
                TimeUtil.format(it.getCreatedAt()),
                true,
                true);
    }

    private Intent findByCode(String code) {
        Intent it = intentMapper.selectOne(
                new LambdaQueryWrapper<Intent>().eq(Intent::getCodeHash, sha256(code)));
        if (it == null) {
            throw new BizException(ErrorCode.INVALID_CODE);
        }
        return it;
    }

    private boolean isTerminal(int status) {
        return status == IntentStatus.SUCCESS.getCode()
                || status == IntentStatus.VOIDED.getCode()
                || status == IntentStatus.CANCELLED.getCode()
                || status == IntentStatus.FINAL_FAILED.getCode();
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
