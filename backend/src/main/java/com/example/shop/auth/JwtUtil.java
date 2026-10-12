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

    public String generate(Long sellerId) {
        return Jwts.builder()
                .subject(String.valueOf(sellerId))
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
}