package com.example.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public interface BuyerAuthDtos {

    record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{3,19}$",
                    message = "用户名需 4-20 位，以字母开头，只能包含字母、数字和下划线")
            String username,
            @NotBlank(message = "密码不能为空")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{6,20}$",
                    message = "密码需 6-20 位，且同时包含字母和数字，不能有空格")
            String password,
            @Size(max = 20, message = "昵称最多 20 个字符")
            String nickname,
            @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确，需为 11 位中国大陆手机号")
            String phone) {
    }

    record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    record BuyerProfile(String id, String username, String phone, String nickname) {
    }

    record AuthResponse(String token, BuyerProfile buyer) {
    }
}
