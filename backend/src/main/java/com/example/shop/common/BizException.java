package com.example.shop.common;

/**
 * 业务异常，携带统一错误码。
 * 订单域起支持附带 data：库存不足时放 shortage 清单，状态不允许时放当前/期望状态。
 */
public class BizException extends RuntimeException {
    private final int code;
    private final Object data;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.data = null;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.data = null;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    public BizException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.code = errorCode.getCode();
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
