package com.example.shop.service;

import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.CommonDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传服务。
 *
 * 三类上传，规则不同（依据需求 A.1-5 裁定）：
 * 1. 商品图库图（/api/upload/image、/api/upload/images）：仅 JPG/PNG，单张 ≤5MB，最多 9 张；
 * 2. 富文本正文内嵌媒体（/api/upload/media）：图片 jpg/jpeg/png/gif/webp，视频 mp4/webm/ogg，
 *    不单独限制格式与大小（需求方裁定），只受传输层 max-file-size 约束；
 * 3. 兼容基线接口，行为不变。
 */
@Service
public class UploadService {

    /** 商品图库：仅 JPG/PNG */
    private static final Set<String> GALLERY_EXT = Set.of("jpg", "jpeg", "png");
    /** 富文本内嵌图片 */
    private static final Set<String> MEDIA_IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    /** 富文本内嵌视频 */
    private static final Set<String> MEDIA_VIDEO_EXT = Set.of("mp4", "webm", "ogg");

    private static final long GALLERY_MAX_SIZE = 5 * 1024 * 1024L;
    private static final long MEDIA_MAX_SIZE = 100 * 1024 * 1024L;
    private static final int MAX_GALLERY_COUNT = 9;

    private final Path uploadDir;

    public UploadService(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** 兼容基线：单张图片上传（按图库规则校验）。 */
    public CommonDtos.UploadResponse upload(MultipartFile file) {
        return new CommonDtos.UploadResponse(save(file, GALLERY_EXT, GALLERY_MAX_SIZE, "图片"));
    }

    /** 商品图库批量上传：一次最多 9 张，任一张不合规则整批失败。 */
    public CommonDtos.UploadListResponse uploadImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请选择要上传的图片");
        }
        if (files.size() > MAX_GALLERY_COUNT) {
            throw new BizException(ErrorCode.IMAGES_TOO_MANY);
        }
        List<String> urls = new ArrayList<>(files.size());
        for (MultipartFile f : files) {
            urls.add(save(f, GALLERY_EXT, GALLERY_MAX_SIZE, "图片"));
        }
        return new CommonDtos.UploadListResponse(urls);
    }

    /** 富文本正文内嵌媒体：图片或视频，不单独限制格式与大小（只做扩展名白名单防脚本上传）。 */
    public CommonDtos.UploadResponse uploadMedia(MultipartFile file) {
        String ext = extensionOf(file);
        boolean isImage = MEDIA_IMAGE_EXT.contains(ext);
        boolean isVideo = MEDIA_VIDEO_EXT.contains(ext);
        if (!isImage && !isVideo) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "正文内嵌媒体仅支持图片(jpg/png/gif/webp)或视频(mp4/webm/ogg)");
        }
        return new CommonDtos.UploadResponse(
                save(file, isImage ? MEDIA_IMAGE_EXT : MEDIA_VIDEO_EXT, MEDIA_MAX_SIZE,
                        isImage ? "图片" : "视频"));
    }

    private String save(MultipartFile file, Set<String> allowedExt, long maxSize, String label) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请选择要上传的" + label);
        }
        if (file.getSize() > maxSize) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    label + "大小不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }
        String ext = extensionOf(file);
        if (!allowedExt.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    label + "仅支持 " + String.join("/", allowedExt.stream().sorted().toList()) + " 格式");
        }
        try {
            Files.createDirectories(uploadDir);
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            // 用 Files.copy 而非 transferTo：后者在部分 Tomcat/Windows 环境下移动临时文件会失败
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, uploadDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
            return "/uploads/" + filename;
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), label + "保存失败: " + e.getMessage());
        }
    }

    private String extensionOf(MultipartFile file) {
        String original = file == null ? null : file.getOriginalFilename();
        if (original == null || !original.contains(".")) {
            return "";
        }
        return original.substring(original.lastIndexOf('.') + 1).toLowerCase();
    }
}
