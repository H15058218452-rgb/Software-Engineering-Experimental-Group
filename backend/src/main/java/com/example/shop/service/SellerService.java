package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.shop.auth.JwtUtil;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.CommonDtos;
import com.example.shop.entity.Seller;
import com.example.shop.mapper.SellerMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerService {

    private final SellerMapper sellerMapper;
    private final BCryptPasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public SellerService(SellerMapper sellerMapper, BCryptPasswordEncoder encoder, JwtUtil jwtUtil) {
        this.sellerMapper = sellerMapper;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    public CommonDtos.LoginResponse login(CommonDtos.LoginRequest req) {
        Seller seller = sellerMapper.selectOne(
                new LambdaQueryWrapper<Seller>().eq(Seller::getUsername, req.username().trim()));
        if (seller == null || !encoder.matches(req.password(), seller.getPasswordHash())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "用户名或密码错误");
        }
        return new CommonDtos.LoginResponse(jwtUtil.generate(seller.getId()));
    }

    @Transactional
    public void changePassword(Long sellerId, CommonDtos.ChangePasswordRequest req) {
        Seller seller = sellerMapper.selectById(sellerId);
        if (seller == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!encoder.matches(req.oldPassword(), seller.getPasswordHash())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "原密码错误");
        }
        Seller update = new Seller();
        update.setId(sellerId);
        update.setPasswordHash(encoder.encode(req.newPassword()));
        sellerMapper.updateById(update);
    }
}