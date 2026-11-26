package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.AddressDtos;
import com.example.shop.entity.Address;
import com.example.shop.mapper.AddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 买家收货地址服务（挑战需求 C2-1：地址增删改查 + 设置默认地址）。
 *
 * 契约口径（《迭代 4 契约》C.2-01）：
 * 1. 同一客户最多 10 条地址，超出报 40952；
 * 2. 默认地址排最前；第一条地址自动成为默认；
 * 3. 设默认走「先清零、再置一」，同事务内完成，不会出现双默认；
 * 4. 删除默认地址后，自动把最近创建的一条提升为默认；
 * 5. 地址不存在报 40951，非本人操作报 40953。
 */
@Service
public class AddressService {

    private static final int MAX_PER_BUYER = 10;
    private static final int DEFAULT = 1;
    private static final int NOT_DEFAULT = 0;

    private final AddressMapper addressMapper;

    public AddressService(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    /** 地址列表：默认地址置顶，其余按创建时间倒序。 */
    public List<AddressDtos.AddressView> list(Long buyerId) {
        requireBuyer(buyerId);
        List<Address> rows = addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getBuyerId, buyerId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getCreatedAt)
                .orderByDesc(Address::getId));
        return rows.stream().map(this::toView).toList();
    }

    @Transactional
    public AddressDtos.AddressView create(Long buyerId, AddressDtos.SaveRequest req) {
        requireBuyer(buyerId);
        validate(req, true);

        long count = addressMapper.selectCount(new LambdaQueryWrapper<Address>()
                .eq(Address::getBuyerId, buyerId));
        if (count >= MAX_PER_BUYER) {
            throw new BizException(ErrorCode.ADDRESS_LIMIT);
        }

        boolean first = count == 0;
        boolean asDefault = first || Boolean.TRUE.equals(req.isDefault());
        if (asDefault) {
            addressMapper.clearDefault(buyerId);
        }

        Address entity = new Address();
        entity.setBuyerId(buyerId);
        applyFields(entity, req, true);
        entity.setIsDefault(asDefault ? DEFAULT : NOT_DEFAULT);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        addressMapper.insert(entity);
        return toView(entity);
    }

    @Transactional
    public AddressDtos.AddressView update(Long buyerId, Long id, AddressDtos.SaveRequest req) {
        Address entity = requireOwned(buyerId, id);
        validate(req, false);
        applyFields(entity, req, false);

        if (Boolean.TRUE.equals(req.isDefault())) {
            addressMapper.clearDefault(buyerId);
            entity.setIsDefault(DEFAULT);
        }
        entity.setUpdatedAt(LocalDateTime.now());
        addressMapper.updateById(entity);
        return toView(entity);
    }

    @Transactional
    public void delete(Long buyerId, Long id) {
        Address entity = requireOwned(buyerId, id);
        addressMapper.deleteById(id);
        if (entity.getIsDefault() != null && entity.getIsDefault() == DEFAULT) {
            promoteLatestCreated(buyerId);
        }
    }

    /** 设置默认地址：该条置 1，同客户其余清零。 */
    @Transactional
    public AddressDtos.AddressView setDefault(Long buyerId, Long id) {
        Address entity = requireOwned(buyerId, id);
        addressMapper.clearDefault(buyerId);
        entity.setIsDefault(DEFAULT);
        entity.setUpdatedAt(LocalDateTime.now());
        addressMapper.updateById(entity);
        return toView(entity);
    }

    // ============ 私有辅助 ============

    /** 删除默认地址后，把最近创建的一条提升为默认；没有地址时不做处理。 */
    private void promoteLatestCreated(Long buyerId) {
        Address latest = addressMapper.selectOne(new LambdaQueryWrapper<Address>()
                .eq(Address::getBuyerId, buyerId)
                .orderByDesc(Address::getCreatedAt)
                .orderByDesc(Address::getId)
                .last("LIMIT 1"));
        if (latest != null) {
            latest.setIsDefault(DEFAULT);
            latest.setUpdatedAt(LocalDateTime.now());
            addressMapper.updateById(latest);
        }
    }

    /** 先判存在（40951），再判归属（40953）。 */
    private Address requireOwned(Long buyerId, Long id) {
        requireBuyer(buyerId);
        Address entity = addressMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        if (!buyerId.equals(entity.getBuyerId())) {
            throw new BizException(ErrorCode.ADDRESS_ACCESS_DENIED);
        }
        return entity;
    }

    private void requireBuyer(Long buyerId) {
        if (buyerId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** 新增时四项必填；修改时允许只传需要变更的字段。 */
    private void validate(AddressDtos.SaveRequest req, boolean full) {
        if (req == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "提交内容为空");
        }
        if (full || req.receiver() != null) {
            requireText(req.receiver(), "收货人不能为空", 20);
        }
        if (full || req.phone() != null) {
            String phone = requireText(req.phone(), "手机号不能为空", 11);
            if (!phone.matches("^\\d{11}$")) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "手机号须为 11 位数字");
            }
        }
        if (full || req.region() != null) {
            requireText(req.region(), "所在地区不能为空", 50);
        }
        if (full || req.detail() != null) {
            requireText(req.detail(), "详细地址不能为空", 200);
        }
    }

    private String requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), message.replace("不能为空", "过长"));
        }
        return trimmed;
    }

    private void applyFields(Address entity, AddressDtos.SaveRequest req, boolean full) {
        if (full || req.receiver() != null) {
            entity.setReceiver(req.receiver().trim());
        }
        if (full || req.phone() != null) {
            entity.setPhone(req.phone().trim());
        }
        if (full || req.region() != null) {
            entity.setRegion(req.region().trim());
        }
        if (full || req.detail() != null) {
            entity.setDetail(req.detail().trim());
        }
    }

    private AddressDtos.AddressView toView(Address a) {
        return new AddressDtos.AddressView(
                String.valueOf(a.getId()),
                a.getReceiver(),
                a.getPhone(),
                a.getRegion(),
                a.getDetail(),
                a.getIsDefault() != null && a.getIsDefault() == DEFAULT);
    }
}
