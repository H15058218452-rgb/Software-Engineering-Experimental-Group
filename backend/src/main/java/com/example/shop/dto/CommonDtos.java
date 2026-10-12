package com.example.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public interface CommonDtos {

    record PageResult<T>(Integer page, Integer pageSize, Long total, List<T> list) {
    }

    record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    record ChangePasswordRequest(
            @NotBlank(message = "原密码不能为空") String oldPassword,
            @NotBlank(message = "新密码不能为空") @Size(min = 8, message = "新密码长度不能少于8位") String newPassword) {
    }

    record LoginResponse(String token) {
    }

    record UploadResponse(String url) {
    }
}