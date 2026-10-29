package com.example.shop.common;

/**
 * 统一错误码（与契约文档第五章保持一致）。
 */
public enum ErrorCode {
    OK(0, "success"),
    PARAM_ERROR(40001, "参数错误"),
    UNAUTHORIZED(40101, "未登录"),
    FORBIDDEN(40301, "无权限"),
    NOT_FOUND(40401, "资源不存在"),
    CONCURRENT_CONFLICT(40901, "并发冲突，请重试"),
    PRODUCT_FROZEN(40902, "商品已冻结"),
    PRODUCT_OFF_SHELF(40903, "商品已下架"),
    DUPLICATE_INTENT(40904, "重复提交意向"),
    INVALID_CODE(40905, "口令码无效"),
    INTENT_STATUS_DENIED(40906, "意向状态不允许操作"),
    NOT_QUEUE_HEAD(40907, "非队首买家"),
    TRADING_CANNOT_UNFREEZE(40908, "交易中商品不可解冻"),
    TRADING_CANNOT_OFF_SHELF(40909, "交易中商品不可下架"),
    TRADING_CANNOT_CANCEL(40910, "交易中买家不可撤销"),
    CATEGORY_INVALID(40911, "类别不合法"),
    CATEGORY_NAME_DUPLICATE(40912, "同级类别名称重复"),
    CATEGORY_HAS_CHILDREN(40913, "类别下还有子类别，不可删除"),
    CATEGORY_HAS_PRODUCTS(40914, "类别下还有商品，不可删除"),
    STOCK_NOT_ENOUGH(40915, "库存不足"),
    STOCK_BELOW_ZERO(40916, "库存不能为负"),
    IMAGES_TOO_MANY(40917, "商品图库最多 9 张"),
    SYSTEM_ERROR(50000, "系统错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
