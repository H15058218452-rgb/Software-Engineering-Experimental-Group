package com.example.shop.dto;

import java.util.List;

/**
 * 订单域 DTO（契约第三章）。ID 与金额一律用字符串，时间用 ISO 8601 带 +08:00。
 */
public interface OrderDtos {

    /** 下单入参：结算接口组装后同进程调用，只含 productId + quantity，不含价格。 */
    record CreateItem(String productId, Integer quantity) {
    }

    record CreateRequest(List<CreateItem> items) {
    }

    record ItemView(String productId, String name, String price, Integer quantity, String subtotal) {
    }

    record OrderView(String orderNo, Integer status, String statusText, String finalResult,
                     String totalAmount, Integer itemCount, String createdAt, List<ItemView> items,
                     String carrier, String trackingNo, String shippedAt) {
    }

    record OrderPage(List<OrderView> list, Long total, Integer page, Integer size) {
    }

    /** 状态推进返回：{ orderNo, status }。 */
    record StatusView(String orderNo, Integer status, String statusText) {
    }

    record Shortage(String productId, String name, Integer required, Integer available) {
    }

    /** 40924 的 data 载荷。 */
    record ShortageData(List<Shortage> shortage) {
    }

    /** 40921 的 data 载荷：当前状态与本次动作要求的前置状态。 */
    record StatusConflict(Integer currentStatus, Integer expectedStatus) {
    }

    /** 发货入参（C2-2）：单号可不填，由后端生成模拟顺丰单号。 */
    record ShipRequest(String trackingNo, String carrier) {
    }

    /** 发货返回：单号与承运商一并回给前端，便于即时展示。 */
    record ShipView(String orderNo, Integer status, String statusText,
                    String carrier, String trackingNo, String shippedAt) {
    }

    /** 物流轨迹节点（契约字段名：time / desc）。 */
    record LogisticsTrace(String time, String desc) {
    }

    /**
     * 物流信息（C2-2），字段与《迭代 4 契约》一致：
     * { logisticsNo, carrier, status, statusText, traces: [{time, desc}] }
     * status 枚举：SHIPPED 已发货 / IN_TRANSIT 运输中 / SIGNED 已签收。
     */
    record LogisticsView(String logisticsNo, String carrier, String status, String statusText,
                         List<LogisticsTrace> traces) {
    }
}
