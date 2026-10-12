package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.dto.CommonDtos;
import com.example.shop.service.UploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/image")
    public ApiResponse<CommonDtos.UploadResponse> image(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(uploadService.upload(file));
    }
}