package com.example.shop.service;

import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.common.TimeUtil;
import com.example.shop.dto.PaymentDtos;
import com.example.shop.entity.Order;
import com.example.shop.entity.Payment;
import com.example.shop.enums.OrderStatus;
import com.example.shop.enums.PayChannel;
import com.example.shop.enums.PayStatus;
import com.example.shop.mapper.OrderMapper;
import com.example.shop.mapper.PaymentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 支付域服务（迭代 4 · C.1-1 / C.1-2）。
 *
 * 设计要点：
 * 1. 不注入 OrderService，避免与 OrderService 的退款调用形成循环依赖；
 *    订单状态推进直接走 OrderMapper 的条件更新（15 → 18）。
 * 2. 一订单一笔有效支付：已存在 PENDING 单时直接返回原单号，不新建（契约 #2 规则）。
 * 3. 回调按 paymentNo 幂等，CAS 保证重复回调只有第一次生效。
 * 4. Mock 网关：不发真实请求，回调由 /api/pay/callback 模拟。
 */
@Service
public class PaymentService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Mock 收银台地址，本期不跳转，只保证字段形状与真实网关一致。 */
    private static final String MOCK_PAY_URL = "https://sandbox.example.com/pay?token=";

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;

    public PaymentService(PaymentMapper paymentMapper, OrderMapper orderMapper) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
    }

    /**
     * 发起支付：校验订单归属与状态，生成支付单。
     * 已存在未完成的支付单时直接返回原 paymentNo，防重复支付。
     */
    @Transactional
    public PaymentDtos.PayView createPay(String orderNo, Long customerId, String channelCode) {
        PayChannel channel = PayChannel.of(channelCode);
        if (channel == null) {
            throw new BizException(ErrorCode.PAY_CHANNEL_INVALID);
        }
        Order order = orderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getCustomerId(), customerId)) {
            throw new BizException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        int status = order.getStatus() == null ? 0 : order.getStatus();
        if (status != OrderStatus.WAIT_PAY.getCode()) {
            throw new BizException(ErrorCode.PAY_STATUS_DENIED,
                    OrderStatus.isPaid(status) ? "订单已支付，无需重复支付" : "订单当前状态为「"
                            + OrderStatus.textOf(status) + "」，无法发起支付");
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getPayDeadline() != null && order.getPayDeadline().isBefore(now)) {
            throw new BizException(ErrorCode.PAY_STATUS_DENIED, "订单已超过支付时限，请重新下单");
        }

        Payment pending = paymentMapper.selectPendingByOrderNo(orderNo);
        if (pending != null) {
            return toPayView(pending, order);
        }

        Payment payment = new Payment();
        payment.setPaymentNo(nextPaymentNo());
        payment.setOrderNo(orderNo);
        payment.setCustomerId(customerId);
        payment.setAmount(order.getTotalAmount());
        payment.setChannel(channel.getCode());
        payment.setStatus(PayStatus.PENDING.getCode());
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        paymentMapper.insert(payment);
        return toPayView(payment, order);
    }

    /** 查询支付结果：前端收银台轮询，直接返回订单状态判断是否已支付成功。 */
    public PaymentDtos.PayQueryView queryPay(String orderNo, Long customerId) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getCustomerId(), customerId)) {
            throw new BizException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        Payment payment = paymentMapper.selectLatestByOrderNo(orderNo);
        if (payment == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        int orderStatus = order.getStatus() == null ? 0 : order.getStatus();
        return new PaymentDtos.PayQueryView(
                order.getOrderNo(),
                orderStatus,
                OrderStatus.textOf(orderStatus),
                payment.getPaymentNo(),
                payment.getChannel(),
                money(payment.getAmount()),
                TimeUtil.format(payment.getPaidAt()));
    }

    /**
     * Mock 网关回调（内部接口）。
     *
     * 故意不加事务：金额不一致 / 订单已关闭这两种失败分支需要先落「支付异常」再报错，
     * 放在同一事务里会被异常回滚掉。状态变更本身都是 CAS，重复回调天然幂等。
     */
    public PaymentDtos.CallbackView callback(PaymentDtos.CallbackRequest request) {
        if (request == null || request.paymentNo() == null || request.paymentNo().isBlank()) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        Payment payment = paymentMapper.selectByNoForUpdate(request.paymentNo().trim());
        if (payment == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        Order order = orderMapper.selectByOrderNo(payment.getOrderNo());
        int orderStatus = order == null || order.getStatus() == null ? 0 : order.getStatus();

        // 已成功 / 已退款 / 异常：幂等返回，不再改状态
        if (!PayStatus.PENDING.getCode().equals(payment.getStatus())) {
            return new PaymentDtos.CallbackView(payment.getPaymentNo(), payment.getOrderNo(),
                    payment.getStatus(), orderStatus);
        }

        // 金额校验：与支付单金额不一致视为异常回调，订单保持 15
        if (request.amount() != null && !request.amount().isBlank()) {
            BigDecimal notified;
            try {
                notified = new BigDecimal(request.amount().trim());
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "回调金额格式非法");
            }
            BigDecimal expected = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
            if (notified.compareTo(expected) != 0) {
                paymentMapper.casAbnormal(payment.getPaymentNo(), "回调金额不一致");
                throw new BizException(ErrorCode.PAY_AMOUNT_MISMATCH);
            }
        }

        // Mock 失败分支：支付单置异常，订单不变，客户可重新发起
        if (Boolean.FALSE.equals(request.success())) {
            String reason = request.failReason() == null || request.failReason().isBlank()
                    ? "模拟支付失败" : request.failReason().trim();
            paymentMapper.casAbnormal(payment.getPaymentNo(), reason);
            return new PaymentDtos.CallbackView(payment.getPaymentNo(), payment.getOrderNo(),
                    PayStatus.ABNORMAL.getCode(), orderStatus);
        }

        String tradeNo = request.tradeNo() == null || request.tradeNo().isBlank()
                ? "MOCK" + System.currentTimeMillis() : request.tradeNo().trim();
        if (paymentMapper.casSuccess(payment.getPaymentNo(), tradeNo) != 1) {
            Payment latest = paymentMapper.selectByNo(payment.getPaymentNo());
            return new PaymentDtos.CallbackView(payment.getPaymentNo(), payment.getOrderNo(),
                    latest == null ? payment.getStatus() : latest.getStatus(), orderStatus);
        }
        if (orderMapper.markPaid(payment.getOrderNo()) != 1) {
            // 订单已被取消或已支付：把支付单退回异常态，避免悬空支付
            paymentMapper.revertSuccess(payment.getPaymentNo(), "订单已关闭，支付未生效");
            throw new BizException(ErrorCode.PAY_STATUS_DENIED, "订单已关闭，支付未生效");
        }
        return new PaymentDtos.CallbackView(payment.getPaymentNo(), payment.getOrderNo(),
                PayStatus.SUCCESS.getCode(), OrderStatus.PAID.getCode());
    }

    /**
     * 订单取消时原路退款（Mock）。没有成功支付单说明未付款，什么都不做。
     * 由 OrderService.cancel 在同一事务内调用。
     */
    @Transactional
    public void refund(String orderNo) {
        Payment payment = paymentMapper.selectSuccessByOrderNo(orderNo);
        if (payment != null) {
            paymentMapper.casRefund(payment.getPaymentNo());
        }
    }

    /** 售后退款用的原支付渠道；无成功支付单时返回 null。 */
    public String refundChannel(String orderNo) {
        Payment payment = paymentMapper.selectSuccessByOrderNo(orderNo);
        return payment == null ? null : payment.getChannel();
    }

    // ============ 私有辅助 ============

    /** PAY + yyyyMMddHHmmss + 3 位随机，唯一键兜底防撞。 */
    private String nextPaymentNo() {
        return "PAY" + LocalDateTime.now().format(NO_TIME) + String.format("%03d", RANDOM.nextInt(1000));
    }

    private PaymentDtos.PayView toPayView(Payment payment, Order order) {
        return new PaymentDtos.PayView(
                payment.getPaymentNo(),
                payment.getOrderNo(),
                payment.getChannel(),
                money(payment.getAmount()),
                MOCK_PAY_URL + payment.getPaymentNo(),
                TimeUtil.format(order.getPayDeadline()));
    }

    private String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
