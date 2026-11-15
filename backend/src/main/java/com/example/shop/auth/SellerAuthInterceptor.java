package com.example.shop.auth;

import com.example.shop.common.ApiResponse;
import com.example.shop.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SellerAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_SELLER_ID = "sellerId";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public SellerAuthInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            Long sellerId = jwtUtil.parse(auth.substring(7));
            if (sellerId != null) {
                request.setAttribute(ATTR_SELLER_ID, sellerId);
                return true;
            }
        }
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(ErrorCode.UNAUTHORIZED)));
        return false;
    }
}