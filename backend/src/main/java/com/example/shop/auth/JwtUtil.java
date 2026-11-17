package com.example.shop.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMs;

    public JwtUtil(@Value("${app.jwt-secret}") String secret,
                   @Value("${app.jwt-expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMs = expireHours * 3600_000L;
    }

    public static final String ROLE_BUYER = "buyer";
    public static final String ROLE_SELLER = "seller";

    /** 卖家 token（历史调用方式，等价于 role=seller）。 */
    public String generate(Long sellerId) {
        return generate(sellerId, ROLE_SELLER);
    }

    /**
     * 订单域需要按 token 判定角色（客户 / 商家），故在 subject 之外增加 role 声明。
     * 迭代 3 之前签发的 token 没有 role，解析结果为 null，调用方按卖家处理。
     */
    public String generate(Long id, String role) {
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireMs))
                .signWith(key)
                .compact();
    }

    public Long parse(String token) {
        try {
            return Long.valueOf(Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    /** 返回 token 中的角色；旧 token 或解析失败返回 null。 */
    public String parseRole(String token) {
        try {
            Object role = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().get("role");
            return role == null ? null : String.valueOf(role);
        } catch (Exception e) {
            return null;
        }
    }
}