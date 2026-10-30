package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.shop.auth.JwtUtil;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.BuyerAuthDtos;
import com.example.shop.entity.Buyer;
import com.example.shop.mapper.BuyerMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 买家账号服务：手机号 + 密码注册/登录，密码仅以 BCrypt 哈希落库。
 * 登录/注册成功即发放 JWT，买家侧接口目前免鉴权，token 供前端保存身份使用。
 */
@Service
public class BuyerAccountService {

    private final BuyerMapper buyerMapper;
    private final BCryptPasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public BuyerAccountService(BuyerMapper buyerMapper, BCryptPasswordEncoder encoder, JwtUtil jwtUtil) {
        this.buyerMapper = buyerMapper;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public BuyerAuthDtos.AuthResponse register(BuyerAuthDtos.RegisterRequest req) {
        String phone = req.phone().trim();
        if (buyerMapper.selectCount(new LambdaQueryWrapper<Buyer>().eq(Buyer::getPhone, phone)) > 0) {
            throw new BizException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
        Buyer buyer = new Buyer();
        buyer.setPhone(phone);
        buyer.setPasswordHash(encoder.encode(req.password()));
        buyer.setNickname(req.nickname() == null || req.nickname().isBlank() ? null : req.nickname().trim());
        try {
            buyerMapper.insert(buyer);
        } catch (DuplicateKeyException e) {
            // 并发注册同一手机号时由唯一索引兜底
            throw new BizException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
        return toAuthResponse(buyer);
    }

    public BuyerAuthDtos.AuthResponse login(BuyerAuthDtos.LoginRequest req) {
        Buyer buyer = buyerMapper.selectOne(
                new LambdaQueryWrapper<Buyer>().eq(Buyer::getPhone, req.phone().trim()));
        if (buyer == null || !encoder.matches(req.password(), buyer.getPasswordHash())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        return toAuthResponse(buyer);
    }

    private BuyerAuthDtos.AuthResponse toAuthResponse(Buyer buyer) {
        BuyerAuthDtos.BuyerProfile profile = new BuyerAuthDtos.BuyerProfile(
                String.valueOf(buyer.getId()), buyer.getPhone(), buyer.getNickname());
        return new BuyerAuthDtos.AuthResponse(jwtUtil.generate(buyer.getId()), profile);
    }
}
