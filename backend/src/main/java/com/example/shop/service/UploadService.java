package com.example.shop.service;

import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.CommonDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class UploadService {

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png");
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    private final Path uploadDir;

    public UploadService(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath();
    }

    public CommonDtos.UploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "图片大小不能超过 5MB");
        }
        String original = file.getOriginalFilename();
        String ext = original == null ? "" : original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "仅支持 JPG/PNG 格式图片");
        }
        try {
            Files.createDirectories(uploadDir);
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            file.transferTo(uploadDir.resolve(filename).toFile());
            return new CommonDtos.UploadResponse("/uploads/" + filename);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "图片保存失败");
        }
    }
}