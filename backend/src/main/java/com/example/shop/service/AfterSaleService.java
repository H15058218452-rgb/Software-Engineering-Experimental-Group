package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.common.TimeUtil;
import com.example.shop.dto.AfterSaleDtos;
import com.example.shop.entity.AfterSale;
import com.example.shop.entity.Order;
import com.example.shop.entity.OrderItem;
import com.example.shop.enums.AfterSaleStatus;
import com.example.shop.enums.OrderStatus;
import com.example.shop.mapper.AfterSaleMapper;
import com.example.shop.mapper.OrderItemMapper;
import com.example.shop.mapper.OrderMapper;
import com.example.shop.mapper.ProductMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 售后服务（迭代 4 · C.3-1 申请/查询/撤销，C.3-2 商家处理）。
 *
 * 规则（契约第六节）：
 * 1. 入口为「已发货 40」与「交易完成 50」，发货前走取消订单；
 * 2. 按「订单 + 商品」粒度申请，同一明细同时只允许一个进行中的售后单，累计不超过 3 次；
 * 3. 退款金额 = 订单明细快照单价 × 申请数量，服务端计算，前端不传；
 * 4. 退货退款：同意后待收货，收到货才退款并回补库存；仅退款：直接退款不补库存；
 * 5. 订单状态在售后全程保持不变。
 */
@Service
public class AfterSaleService {

    private static final int MAX_TIMES = 3;
    private static final int MAX_IMAGES = 5;
    private static final int MAX_TITLE = 50;
    private static final int MAX_DESCRIPTION = 500;
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final AfterSaleMapper afterSaleMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public AfterSaleService(AfterSaleMapper afterSaleMapper, OrderMapper orderMapper,
                            OrderItemMapper orderItemMapper, ProductMapper productMapper,
                            PaymentService paymentService, ObjectMapper objectMapper) {
        this.afterSaleMapper = afterSaleMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    // ============ 买家侧 ============

    /** 买家提交售后申请：成功后状态 PENDING，订单状态不变。 */
    @Transactional
    public AfterSaleDtos.AfterSaleView create(Long customerId, AfterSaleDtos.CreateRequest request) {
        if (customerId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (request == null || isBlank(request.orderNo())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "订单号不能为空");
        }
        String title = trim(request.title());
        if (title == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "售后标题不能为空");
        }
        if (title.length() > MAX_TITLE) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "售后标题最长 " + MAX_TITLE + " 字");
        }
        String description = trim(request.description());
        if (description == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "问题描述不能为空");
        }
        if (description.length() > MAX_DESCRIPTION) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "问题描述最长 " + MAX_DESCRIPTION + " 字");
        }
        List<String> images = normalizeImages(request.images());
        if (images.size() > MAX_IMAGES) {
            throw new BizException(ErrorCode.AFTER_SALE_IMAGE_LIMIT);
        }

        Order order = orderMapper.selectByOrderNoForUpdate(request.orderNo().trim());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getCustomerId(), customerId)) {
            throw new BizException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        int orderStatus = order.getStatus() == null ? 0 : order.getStatus();
        if (orderStatus != OrderStatus.SHIPPED.getCode()
                && orderStatus != OrderStatus.COMPLETED.getCode()) {
            throw new BizException(ErrorCode.AFTER_SALE_NOT_ALLOWED);
        }

        Long productId = parseLong(request.productId());
        if (productId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "商品 ID 不能为空");
        }
        OrderItem item = orderItemMapper.selectByOrderIdAndProductId(order.getId(), productId);
        if (item == null) {
            throw new BizException(ErrorCode.AFTER_SALE_NOT_ALLOWED, "该商品不在本订单中");
        }

        int bought = item.getQuantity() == null ? 0 : item.getQuantity();
        int quantity = request.quantity() == null ? bought : request.quantity();
        if (quantity <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "售后数量必须大于 0");
        }
        if (quantity > bought) {
            throw new BizException(ErrorCode.AFTER_SALE_QUANTITY_DENIED);
        }

        if (afterSaleMapper.countActiveByItem(item.getId()) > 0) {
            throw new BizException(ErrorCode.AFTER_SALE_EXISTS);
        }
        if (afterSaleMapper.countByItem(item.getId()) >= MAX_TIMES) {
            throw new BizException(ErrorCode.AFTER_SALE_LIMIT);
        }

        LocalDateTime now = LocalDateTime.now();
        AfterSale afterSale = new AfterSale();
        afterSale.setAfterSaleNo(nextNo());
        afterSale.setOrderNo(order.getOrderNo());
        afterSale.setOrderItemId(item.getId());
        afterSale.setProductId(item.getProductId());
        afterSale.setProductName(item.getProductName());
        afterSale.setCustomerId(customerId);
        afterSale.setQuantity(quantity);
        afterSale.setTitle(title);
        afterSale.setDescription(description);
        afterSale.setImages(writeImages(images));
        afterSale.setStatus(AfterSaleStatus.PENDING.getCode());
        afterSale.setCreatedAt(now);
        afterSale.setUpdatedAt(now);
        afterSaleMapper.insert(afterSale);
        return toView(afterSale);
    }

    /** 客户查看自己的售后单列表，可按状态筛选。 */
    public AfterSaleDtos.AfterSalePage mine(Long customerId, String status, int page, int size) {
        if (customerId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Page<AfterSale> result = afterSaleMapper.selectPage(pageOf(page, size),
                new LambdaQueryWrapper<AfterSale>()
                        .eq(AfterSale::getCustomerId, customerId)
                        .eq(!isBlank(status), AfterSale::getStatus, normalizeStatus(status))
                        .orderByDesc(AfterSale::getId));
        return toPage(result, page, size);
    }

    /** 商家查看全部售后单，可按状态筛选。 */
    public AfterSaleDtos.AfterSalePage sellerPage(String status, int page, int size) {
        Page<AfterSale> result = afterSaleMapper.selectPage(pageOf(page, size),
                new LambdaQueryWrapper<AfterSale>()
                        .eq(!isBlank(status), AfterSale::getStatus, normalizeStatus(status))
                        .orderByDesc(AfterSale::getId));
        return toPage(result, page, size);
    }

    /** 售后详情：买家只能看自己的（40967），商家可看全部。 */
    public AfterSaleDtos.AfterSaleView detail(String afterSaleNo, Long actorId, boolean seller) {
        AfterSale afterSale = afterSaleMapper.selectByNo(afterSaleNo);
        if (afterSale == null) {
            throw new BizException(ErrorCode.AFTER_SALE_NOT_FOUND);
        }
        if (!seller && !Objects.equals(afterSale.getCustomerId(), actorId)) {
            throw new BizException(ErrorCode.AFTER_SALE_ACCESS_DENIED);
        }
        return toView(afterSale);
    }

    /** 客户撤销售后：只有 PENDING 可撤销，不退款、不补库存、订单不变。 */
    @Transactional
    public AfterSaleDtos.StatusView cancel(String afterSaleNo, Long customerId) {
        if (customerId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        AfterSale afterSale = afterSaleMapper.selectByNoForUpdate(afterSaleNo);
        if (afterSale == null) {
            throw new BizException(ErrorCode.AFTER_SALE_NOT_FOUND);
        }
        if (!Objects.equals(afterSale.getCustomerId(), customerId)) {
            throw new BizException(ErrorCode.AFTER_SALE_ACCESS_DENIED);
        }
        if (afterSaleMapper.casStatus(afterSaleNo, AfterSaleStatus.PENDING.getCode(),
                AfterSaleStatus.CANCELLED.getCode()) != 1) {
            throw new BizException(ErrorCode.AFTER_SALE_STATUS_DENIED, "只有「待处理」的售后单可以撤销");
        }
        return statusView(afterSaleNo, AfterSaleStatus.CANCELLED);
    }

    // ============ 商家侧处理（C.3-2） ============

    /** 同意退货：PENDING → WAIT_RETURN，此时不退款、不回补库存。 */
    @Transactional
    public AfterSaleDtos.StatusView agreeReturn(String afterSaleNo) {
        requireExistsForUpdate(afterSaleNo);
        if (afterSaleMapper.casAgreeReturn(afterSaleNo) != 1) {
            throw new BizException(ErrorCode.AFTER_SALE_STATUS_DENIED, "只有「待处理」的售后单可以同意退货");
        }
        return statusView(afterSaleNo, AfterSaleStatus.WAIT_RETURN);
    }

    /** 确认收到退货并退款：回补库存（每件 +1）→ 原路退款 → 售后单 REFUNDED。 */
    @Transactional
    public AfterSaleDtos.StatusView receive(String afterSaleNo) {
        AfterSale afterSale = requireExistsForUpdate(afterSaleNo);
        if (!AfterSaleStatus.WAIT_RETURN.getCode().equals(afterSale.getStatus())) {
            throw new BizException(ErrorCode.AFTER_SALE_STATUS_DENIED, "只有「待收货」的售后单可以确认收货");
        }
        int quantity = afterSale.getQuantity() == null ? 0 : afterSale.getQuantity();
        productMapper.restoreStock(afterSale.getProductId(), quantity);
        productMapper.restoreOnSaleIfSoldOut(afterSale.getProductId());
        BigDecimal refundAmount = refundAmountOf(afterSale);
        String channel = paymentService.refundChannel(afterSale.getOrderNo());
        if (afterSaleMapper.casReceiveRefund(afterSaleNo, refundAmount, channel) != 1) {
            throw new BizException(ErrorCode.AFTER_SALE_STATUS_DENIED);
        }
        return statusView(afterSaleNo, AfterSaleStatus.REFUNDED);
    }

    /** 仅退款不退货：原路退款，不回补库存。 */
    @Transactional
    public AfterSaleDtos.StatusView refundOnly(String afterSaleNo) {
        requireExistsForUpdate(afterSaleNo);
        BigDecimal refundAmount = refundAmountOf(afterSaleMapper.selectByNo(afterSaleNo));
        String channel = paymentService.refundChannel(
                afterSaleMapper.selectByNo(afterSaleNo).getOrderNo());
        if (afterSaleMapper.casRefundOnly(afterSaleNo, refundAmount, channel) != 1) {
            throw new BizException(ErrorCode.AFTER_SALE_STATUS_DENIED, "只有「待处理」的售后单可以仅退款");
        }
        return statusView(afterSaleNo, AfterSaleStatus.REFUNDED);
    }

    /** 拒绝售后：PENDING → REJECTED，客户可再次申请（计入 3 次上限）。 */
    @Transactional
    public AfterSaleDtos.StatusView reject(String afterSaleNo) {
        requireExistsForUpdate(afterSaleNo);
        if (afterSaleMapper.casStatus(afterSaleNo, AfterSaleStatus.PENDING.getCode(),
                AfterSaleStatus.REJECTED.getCode()) != 1) {
            throw new BizException(ErrorCode.AFTER_SALE_STATUS_DENIED, "只有「待处理」的售后单可以拒绝");
        }
        return statusView(afterSaleNo, AfterSaleStatus.REJECTED);
    }

    // ============ 私有辅助 ============

    private AfterSale requireExistsForUpdate(String afterSaleNo) {
        AfterSale afterSale = afterSaleMapper.selectByNoForUpdate(afterSaleNo);
        if (afterSale == null) {
            throw new BizException(ErrorCode.AFTER_SALE_NOT_FOUND);
        }
        return afterSale;
    }

    /** 退款金额 = 订单明细快照单价 × 申请数量，服务端计算。 */
    private BigDecimal refundAmountOf(AfterSale afterSale) {
        OrderItem item = orderItemMapper.selectById(afterSale.getOrderItemId());
        if (item == null || item.getPrice() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        int quantity = afterSale.getQuantity() == null ? 0 : afterSale.getQuantity();
        return item.getPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    private AfterSaleDtos.StatusView statusView(String afterSaleNo, AfterSaleStatus status) {
        return new AfterSaleDtos.StatusView(afterSaleNo, status.getCode(), status.getText());
    }

    private Page<AfterSale> pageOf(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return new Page<>(safePage, safeSize);
    }

    private AfterSaleDtos.AfterSalePage toPage(Page<AfterSale> result, int page, int size) {
        List<AfterSaleDtos.AfterSaleView> list = new ArrayList<>();
        for (AfterSale afterSale : result.getRecords()) {
            list.add(toView(afterSale));
        }
        return new AfterSaleDtos.AfterSalePage(list, result.getTotal(), page, size);
    }

    private AfterSaleDtos.AfterSaleView toView(AfterSale afterSale) {
        return new AfterSaleDtos.AfterSaleView(
                afterSale.getAfterSaleNo(),
                afterSale.getOrderNo(),
                afterSale.getProductId() == null ? null : String.valueOf(afterSale.getProductId()),
                afterSale.getProductName(),
                afterSale.getQuantity(),
                afterSale.getTitle(),
                afterSale.getDescription(),
                readImages(afterSale.getImages()),
                afterSale.getType(),
                afterSale.getStatus(),
                AfterSaleStatus.textOf(afterSale.getStatus()),
                afterSale.getRefundAmount() == null ? null
                        : afterSale.getRefundAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                afterSale.getRefundChannel(),
                TimeUtil.format(afterSale.getRefundedAt()),
                TimeUtil.format(afterSale.getCreatedAt()));
    }

    private String nextNo() {
        String prefix = "AS" + LocalDate.now().format(DAY) + "-";
        String max = afterSaleMapper.selectMaxNoOfDay(prefix);
        int seq = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                seq = 1;
            }
        }
        return prefix + String.format("%04d", seq);
    }

    /** 状态筛选参数容错：统一大写，非法值查不到数据但不报错。 */
    private String normalizeStatus(String status) {
        return isBlank(status) ? null : status.trim().toUpperCase();
    }

    private List<String> normalizeImages(List<String> images) {
        List<String> result = new ArrayList<>();
        if (images == null) {
            return result;
        }
        for (String image : images) {
            if (image != null && !image.isBlank()) {
                result.add(image.trim());
            }
        }
        return result;
    }

    private String writeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private List<String> readImages(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() { });
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
