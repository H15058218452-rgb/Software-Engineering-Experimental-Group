package com.example.shop.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：统一转换为 { code, message, data } 结构。
 * 业务错误码为权威，HTTP 状态统一返回 200（业务错误）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e) {
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : ErrorCode.PARAM_ERROR.getMessage();
        return ApiResponse.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ApiResponse<Void> handleBadRequest(Exception e) {
        return ApiResponse.fail(ErrorCode.PARAM_ERROR);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        return ApiResponse.fail(ErrorCode.PARAM_ERROR.getCode(), "图片大小不能超过 5MB");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Void> handleNoResource(NoResourceFoundException e) {
        return ApiResponse.fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<Void> handleDuplicateKey(DuplicateKeyException e) {
        return ApiResponse.fail(ErrorCode.CONCURRENT_CONFLICT.getCode(), "操作冲突，请刷新后重试");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR);
    }
}
