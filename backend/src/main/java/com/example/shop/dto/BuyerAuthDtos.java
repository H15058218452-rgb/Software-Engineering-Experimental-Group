package com.example.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public interface BuyerAuthDtos {

    record RegisterRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确，需为 11 位中国大陆手机号")
            String phone,
            @NotBlank(message = "密码不能为空")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{6,20}$",
                    message = "密码需 6-20 位，且同时包含字母和数字，不能有空格")
            String password,
            @Size(max = 20, message = "昵称最多 20 个字符")
            String nickname) {
    }

    record LoginRequest(
            @NotBlank(message = "手机号不能为空") String phone,
            @NotBlank(message = "密码不能为空") String password) {
    }

    record BuyerProfile(String id, String phone, String nickname) {
    }

    record AuthResponse(String token, BuyerProfile buyer) {
    }
}
