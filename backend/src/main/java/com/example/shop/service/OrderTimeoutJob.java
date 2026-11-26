package com.example.shop.service;

import com.example.shop.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 待支付超时取消（C.1-2）。
 *
 * 每 60 秒扫一次 status=15 且 pay_deadline 已过的订单，逐笔取消并回补库存。
 * 单笔失败只记日志，不中断整批；CAS 保证与支付回调并发时只有一方成功。
 */
@Component
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);
    private static final int BATCH = 100;

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    public OrderTimeoutJob(OrderMapper orderMapper, OrderService orderService) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void cancelExpiredOrders() {
        List<String> orderNos = orderMapper.selectExpiredWaitPay(BATCH);
        if (orderNos.isEmpty()) {
            return;
        }
        for (String orderNo : orderNos) {
            try {
                if (orderService.cancelExpired(orderNo)) {
                    log.info("待支付超时自动取消：orderNo={}", orderNo);
                }
            } catch (RuntimeException e) {
                log.warn("超时取消失败：orderNo={}, reason={}", orderNo, e.getMessage());
            }
        }
    }
}
