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
 * 默认地址约束：
 * 1. 同一买家任意时刻最多一条 is_default = 1；
 * 2. 第一条地址自动成为默认；
 * 3. 删除默认地址后，自动把最近更新的一条提升为默认，避免出现“无默认”状态；
 * 4. 设置默认走“先清零、再置一”，两条语句在同一事务内，并发下也不会出现双默认。
 */
@Service
public class AddressService {

    private static final int DEFAULT = 1;
    private static final int NOT_DEFAULT = 0;

    private final AddressMapper addressMapper;

    public AddressService(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    /** 地址列表：默认地址置顶，其余按更新时间倒序。 */
    public List<AddressDtos.AddressView> list(Long buyerId) {
        requireBuyer(buyerId);
        List<Address> rows = addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getBuyerId, buyerId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getUpdatedAt)
                .orderByDesc(Address::getId));
        return rows.stream().map(this::toView).toList();
    }

    @Transactional
    public AddressDtos.AddressView create(Long buyerId, AddressDtos.SaveRequest req) {
        requireBuyer(buyerId);
        validate(req, true);

        boolean first = addressMapper.selectCount(new LambdaQueryWrapper<Address>()
                .eq(Address::getBuyerId, buyerId)) == 0;
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
            promoteLatest(buyerId);
        }
    }

    /** 设置默认地址：把该条置为默认，其余清零。 */
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

    /** 删除默认地址后，把最近更新的一条提升为默认；没有地址时不做处理。 */
    private void promoteLatest(Long buyerId) {
        Address latest = addressMapper.selectOne(new LambdaQueryWrapper<Address>()
                .eq(Address::getBuyerId, buyerId)
                .orderByDesc(Address::getUpdatedAt)
                .orderByDesc(Address::getId)
                .last("LIMIT 1"));
        if (latest != null) {
            latest.setIsDefault(DEFAULT);
            latest.setUpdatedAt(LocalDateTime.now());
            addressMapper.updateById(latest);
        }
    }

    private Address requireOwned(Long buyerId, Long id) {
        requireBuyer(buyerId);
        Address entity = addressMapper.selectById(id);
        if (entity == null || !buyerId.equals(entity.getBuyerId())) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "收货地址不存在");
        }
        return entity;
    }

    private void requireBuyer(Long buyerId) {
        if (buyerId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** 新增时全部字段必填；修改时允许只传需要变更的字段。 */
    private void validate(AddressDtos.SaveRequest req, boolean full) {
        if (req == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请求体不能为空");
        }
        if (full || req.receiverName() != null) {
            requireText(req.receiverName(), "收货人不能为空", 32);
        }
        if (full || req.receiverPhone() != null) {
            String phone = requireText(req.receiverPhone(), "手机号不能为空", 20);
            if (!phone.matches("^1\\d{10}$")) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "手机号格式不正确");
            }
        }
        if (full || req.province() != null) {
            requireText(req.province(), "省份不能为空", 32);
        }
        if (full || req.city() != null) {
            requireText(req.city(), "城市不能为空", 32);
        }
        if (full || req.detail() != null) {
            requireText(req.detail(), "详细地址不能为空", 200);
        }
        if (req.district() != null && req.district().trim().length() > 32) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "区县不能超过 32 字");
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
        if (full || req.receiverName() != null) {
            entity.setReceiverName(req.receiverName().trim());
        }
        if (full || req.receiverPhone() != null) {
            entity.setReceiverPhone(req.receiverPhone().trim());
        }
        if (full || req.province() != null) {
            entity.setProvince(req.province().trim());
        }
        if (full || req.city() != null) {
            entity.setCity(req.city().trim());
        }
        if (full || req.district() != null) {
            entity.setDistrict(req.district() == null ? null : req.district().trim());
        }
        if (full || req.detail() != null) {
            entity.setDetail(req.detail().trim());
        }
    }

    private AddressDtos.AddressView toView(Address a) {
        String full = a.getProvince() + a.getCity()
                + (a.getDistrict() == null || a.getDistrict().isBlank() ? "" : a.getDistrict())
                + a.getDetail();
        return new AddressDtos.AddressView(
                String.valueOf(a.getId()),
                a.getReceiverName(),
                a.getReceiverPhone(),
                a.getProvince(),
                a.getCity(),
                a.getDistrict(),
                a.getDetail(),
                full,
                a.getIsDefault() != null && a.getIsDefault() == DEFAULT);
    }
}
