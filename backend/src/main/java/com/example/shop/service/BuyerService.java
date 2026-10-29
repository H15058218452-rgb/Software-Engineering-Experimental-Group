package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.common.TimeUtil;
import com.example.shop.dto.BuyerDtos;
import com.example.shop.dto.ProductDtos;
import com.example.shop.entity.Intent;
import com.example.shop.entity.Product;
import com.example.shop.enums.IntentStatus;
import com.example.shop.enums.ProductStatus;
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

@Service
public class BuyerService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProductMapper productMapper;
    private final IntentMapper intentMapper;

    public BuyerService(ProductMapper productMapper, IntentMapper intentMapper) {
        this.productMapper = productMapper;
        this.intentMapper = intentMapper;
    }

    @Transactional
    public BuyerDtos.SubmitIntentResponse submit(BuyerDtos.SubmitIntentRequest req) {
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
        if (intentMapper.countActiveByPhone(p.getId(), req.phone().trim()) > 0) {
            throw new BizException(ErrorCode.DUPLICATE_INTENT);
        }
        String code = generateCode();
        Intent it = new Intent();
        it.setProductId(p.getId());
        it.setCodeHash(sha256(code));
        it.setName(req.name().trim());
        it.setPhone(req.phone().trim());
        it.setStatus(IntentStatus.QUEUING.getCode());
        it.setQueuedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        it.setFailCount(0);
        it.setCreatedAt(LocalDateTime.now());
        it.setUpdatedAt(LocalDateTime.now());
        intentMapper.insert(it);
        int position = intentMapper.calcQueuePosition(p.getId(), it.getQueuedAt(), it.getId());
        return new BuyerDtos.SubmitIntentResponse(code, toIntentView(it, position));
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
