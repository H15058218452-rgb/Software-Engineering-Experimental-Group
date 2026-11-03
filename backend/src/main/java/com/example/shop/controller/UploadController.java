package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.dto.CommonDtos;
import com.example.shop.service.UploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /** 兼容基线：单张商品图（JPG/PNG、≤5MB）。 */
    @PostMapping("/image")
    public ApiResponse<CommonDtos.UploadResponse> image(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(uploadService.upload(file));
    }

    /** 商品图库批量上传（A.1-5）：一次最多 9 张，JPG/PNG，单张 ≤5MB。 */
    @PostMapping("/images")
    public ApiResponse<CommonDtos.UploadListResponse> images(@RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.ok(uploadService.uploadImages(files));
    }

    /** 富文本正文内嵌媒体（A.1-5）：图片或视频，不单独限制格式与大小。 */
    @PostMapping("/media")
    public ApiResponse<CommonDtos.UploadResponse> media(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(uploadService.uploadMedia(file));
    }
}