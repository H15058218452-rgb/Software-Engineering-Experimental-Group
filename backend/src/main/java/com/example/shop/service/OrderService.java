package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.common.TimeUtil;
import com.example.shop.dto.OrderDtos;
import com.example.shop.entity.Order;
import com.example.shop.entity.OrderItem;
import com.example.shop.entity.Product;
import com.example.shop.enums.OrderAction;
import com.example.shop.enums.OrderStatus;
import com.example.shop.enums.ProductStatus;
import com.example.shop.mapper.OrderItemMapper;
import com.example.shop.entity.Logistics;
import com.example.shop.mapper.LogisticsMapper;
import com.example.shop.mapper.OrderItemMapper;
import com.example.shop.mapper.OrderMapper;
import com.example.shop.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单域服务（迭代 3 · 挑战需求 B.1）。
 *
 * 职责边界（契约第五章）：
 * 1. 库存校验与扣减、单价定格、金额计算、订单号生成、扣到 0 自动下架 —— 全部在本服务内完成；
 * 2. order / order_item 只由本服务写入，结算接口只读购物车、不碰订单表、不重复扣库存。
 *
 * 一致性：
 * - 下单按 productId 升序 SELECT ... FOR UPDATE 锁行，避免多订单交叉加锁死锁；
 * - 扣减用条件更新兜底，即使漏锁也不会扣成负数；
 * - 状态变更用 CAS，并发下重复推进只有一次成功。
 */
@Service
public class OrderService {

    private static final int MAX_ITEMS = 20;
    private static final int MAX_QUANTITY = 99;
    /** 下单后支付时限（小时），超时由 OrderTimeoutJob 自动取消并回补库存。 */
    private static final int PAY_TIMEOUT_HOURS = 24;
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final LogisticsMapper logisticsMapper;
    private final PaymentService paymentService;

    public OrderService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                        ProductMapper productMapper, LogisticsMapper logisticsMapper,
                        PaymentService paymentService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.logisticsMapper = logisticsMapper;
        this.paymentService = paymentService;
    }

    // ============ 下单 ============

    /**
     * 创建订单：校验 → 锁商品 → 校验库存 → 扣库存 → 落库。
     * 任一条库存不足则整单不生成，返回 40924 并附 shortage 清单。
     */
    @Transactional
    public OrderDtos.OrderView create(Long customerId, List<OrderDtos.CreateItem> items) {
        if (customerId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Map<Long, Integer> merged = mergeItems(items);

        // 按 id 升序加锁，避免两笔订单以相反顺序锁同一批商品造成死锁
        List<Long> productIds = new ArrayList<>(merged.keySet());
        Collections.sort(productIds);

        List<Line> lines = new ArrayList<>();
        List<OrderDtos.Shortage> shortages = new ArrayList<>();

        for (Long productId : productIds) {
            int quantity = merged.get(productId);
            Product p = productMapper.selectByIdForUpdate(productId);
            if (p == null) {
                throw new BizException(ErrorCode.NOT_FOUND.getCode(), "商品不存在：" + productId);
            }
            if (p.getStatus() == null || p.getStatus() != ProductStatus.ON_SALE.getCode()) {
                throw new BizException(ErrorCode.ORDER_PRODUCT_OFF_SHELF.getCode(),
                        "商品「" + p.getName() + "」已下架，请从购物车移除后重试");
            }
            int stock = p.getStock() == null ? 0 : p.getStock();
            if (stock < quantity) {
                shortages.add(new OrderDtos.Shortage(String.valueOf(productId), p.getName(), quantity, stock));
                continue;
            }
            lines.add(new Line(productId, p.getName(), p.getPrice(), quantity));
        }

        if (!shortages.isEmpty()) {
            throw new BizException(ErrorCode.ORDER_STOCK_NOT_ENOUGH, "部分商品库存不足",
                    new OrderDtos.ShortageData(shortages));
        }

        // 同一事务内逐条扣减，任一条失败整单回滚
        for (Line line : lines) {
            if (productMapper.deductStock(line.productId, line.quantity) != 1) {
                Product latest = productMapper.selectById(line.productId);
                int available = latest == null || latest.getStock() == null ? 0 : latest.getStock();
                throw new BizException(ErrorCode.ORDER_STOCK_NOT_ENOUGH, "部分商品库存不足",
                        new OrderDtos.ShortageData(List.of(new OrderDtos.Shortage(
                                String.valueOf(line.productId), line.name, line.quantity, available))));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setOrderNo(nextOrderNo());
        order.setCustomerId(customerId);
        order.setTotalAmount(lines.stream()
                .map(l -> l.subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP));
        order.setStatus(OrderStatus.WAIT_PAY.getCode());
        order.setPayDeadline(now.plusHours(PAY_TIMEOUT_HOURS));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        List<OrderItem> entities = new ArrayList<>();
        for (Line line : lines) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(line.productId);
            item.setProductName(line.name);
            item.setPrice(line.price);
            item.setQuantity(line.quantity);
            item.setSubtotal(line.subtotal);
            orderItemMapper.insert(item);
            entities.add(item);
        }
        return toView(order, entities);
    }

    // ============ 状态推进 / 取消 ============

    /** 商家正向推进：confirm / ready / ship，前置状态不匹配一律 40921。交易完成改由客户确认收货。 */
    @Transactional
    public OrderDtos.StatusView advance(String orderNo, OrderAction action) {
        Order order = lockOrder(orderNo);
        int current = order.getStatus();
        if (OrderStatus.isFinal(current)) {
            throw new BizException(ErrorCode.ORDER_FINALIZED);
        }
        int expected = action.getFrom().getCode();
        if (current != expected) {
            throw new BizException(ErrorCode.ORDER_STATUS_DENIED,
                    "订单当前状态为「" + OrderStatus.textOf(current) + "」，不能执行「" + action.getCode() + "」",
                    new OrderDtos.StatusConflict(current, expected));
        }
        if (orderMapper.casStatus(orderNo, expected, action.getTo().getCode()) != 1) {
            Order latest = orderMapper.selectByOrderNo(orderNo);
            int now = latest == null ? current : latest.getStatus();
            throw new BizException(ErrorCode.ORDER_STATUS_DENIED,
                    "订单状态已变更，请刷新后重试",
                    new OrderDtos.StatusConflict(now, expected));
        }
        return new OrderDtos.StatusView(orderNo, action.getTo().getCode(), action.getTo().getText());
    }

    /**
     * 取消订单（C.1-1 规则）。
     *
     * 客户：10/15/18/20/30 可取消，已发货后不可取消；
     * 商家：额外允许 40 已发货，但款项退回后库存不回补（货已在途）。
     * 已支付（18 及之后）取消时同事务发起原路退款；未发货状态同事务回补库存。
     */
    @Transactional
    public OrderDtos.StatusView cancel(String orderNo, Long actorId, boolean seller) {
        Order order = lockOrder(orderNo);
        int current = order.getStatus();

        if (!seller) {
            if (!Objects.equals(order.getCustomerId(), actorId)) {
                throw new BizException(ErrorCode.ORDER_ACCESS_DENIED);
            }
        } else if (actorId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        if (OrderStatus.isFinal(current)) {
            throw new BizException(ErrorCode.ORDER_FINALIZED);
        }

        boolean allowed = seller ? isSellerCancellable(current) : isCustomerCancellable(current);
        if (!allowed) {
            throw new BizException(ErrorCode.ORDER_STATUS_DENIED,
                    seller ? "订单已完成或已取消，不可取消" : "订单已发货，客户不可取消，请联系商家",
                    new OrderDtos.StatusConflict(current, null));
        }

        // 已发货取消：货已在途，库存不回补，只退款
        if (current != OrderStatus.SHIPPED.getCode()) {
            restoreStock(order);
        }
        // 已支付订单：原路退款，与状态变更同事务
        if (OrderStatus.isPaid(current)) {
            paymentService.refund(orderNo);
        }
        if (orderMapper.casStatus(orderNo, current, OrderStatus.CANCELLED.getCode()) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return new OrderDtos.StatusView(orderNo, OrderStatus.CANCELLED.getCode(),
                OrderStatus.CANCELLED.getText());
    }

    // ============ 发货与物流（挑战需求 C2-2） ============

    private static final String LOGISTICS_SHIPPED = "SHIPPED";
    private static final String LOGISTICS_IN_TRANSIT = "IN_TRANSIT";
    private static final String LOGISTICS_SIGNED = "SIGNED";

    /**
     * 卖家发货（C.2-03）：仅「备货完成 30」可发货，物流单号与快递公司均必填（空报 40972）。
     * 状态推进与物流记录在同一事务内完成，避免出现已发货却没有物流信息的中间态。
     */
    @Transactional
    public OrderDtos.ShipView ship(String orderNo, OrderDtos.ShipRequest req) {
        String logisticsNo = requireLogisticsNo(req);
        String carrier = requireCarrier(req);

        Order order = lockOrder(orderNo);
        int current = order.getStatus();
        if (OrderStatus.isFinal(current)) {
            throw new BizException(ErrorCode.ORDER_FINALIZED);
        }
        int expected = OrderStatus.READY.getCode();
        int to = OrderStatus.SHIPPED.getCode();
        if (current != expected) {
            throw new BizException(ErrorCode.ORDER_STATUS_DENIED,
                    "订单当前状态为「" + OrderStatus.textOf(current) + "」，不能执行「ship」",
                    new OrderDtos.StatusConflict(current, expected));
        }
        if (orderMapper.casStatus(orderNo, expected, to) != 1) {
            Order latest = orderMapper.selectByOrderNo(orderNo);
            int nowStatus = latest == null ? current : latest.getStatus();
            throw new BizException(ErrorCode.ORDER_STATUS_DENIED,
                    "订单状态已变更，请刷新后重试",
                    new OrderDtos.StatusConflict(nowStatus, expected));
        }

        Logistics lg = new Logistics();
        lg.setOrderNo(orderNo);
        lg.setLogisticsNo(logisticsNo);
        lg.setCarrier(carrier);
        lg.setStatus(LOGISTICS_SHIPPED);
        lg.setCreatedAt(LocalDateTime.now());
        lg.setUpdatedAt(LocalDateTime.now());
        logisticsMapper.insert(lg);
        return new OrderDtos.ShipView(orderNo, logisticsNo, carrier,
                LOGISTICS_SHIPPED, logisticsStatusText(LOGISTICS_SHIPPED));
    }

    /** 修改物流单号（C.2-03）：仅「已发货 40」且客户未签收时可改。 */
    @Transactional
    public OrderDtos.ShipView updateLogistics(String orderNo, OrderDtos.ShipRequest req) {
        String logisticsNo = requireLogisticsNo(req);
        String carrier = requireCarrier(req);

        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        int expected = OrderStatus.SHIPPED.getCode();
        int current = order.getStatus();
        if (OrderStatus.isFinal(current)) {
            throw new BizException(ErrorCode.ORDER_FINALIZED);
        }
        if (current != expected) {
            throw new BizException(ErrorCode.ORDER_STATUS_DENIED,
                    "订单当前状态为「" + OrderStatus.textOf(current) + "」，只有已发货可修改物流",
                    new OrderDtos.StatusConflict(current, expected));
        }
        Logistics lg = logisticsMapper.selectByOrderNo(orderNo);
        if (lg == null) {
            throw new BizException(ErrorCode.LOGISTICS_NOT_SHIPPED);
        }
        lg.setLogisticsNo(logisticsNo);
        lg.setCarrier(carrier);
        lg.setUpdatedAt(LocalDateTime.now());
        logisticsMapper.updateById(lg);
        return new OrderDtos.ShipView(orderNo, logisticsNo, carrier,
                lg.getStatus(), logisticsStatusText(lg.getStatus()));
    }

    /**
     * 物流信息查询（契约 C.2-04）：订单 40 及之后可查，未发货返回 40971；买家只能查自己的订单。
     * 轨迹由「模拟顺丰服务」按发货时间推算，不依赖外部网络，便于离线演示与验收。
     */
    public OrderDtos.LogisticsView logistics(String orderNo, Long actorId, boolean seller) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!seller && !Objects.equals(order.getCustomerId(), actorId)) {
            throw new BizException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        Logistics lg = logisticsMapper.selectByOrderNo(orderNo);
        if (lg == null) {
            throw new BizException(ErrorCode.LOGISTICS_NOT_SHIPPED);
        }

        LocalDateTime shippedAt = lg.getCreatedAt();
        LocalDateTime now = LocalDateTime.now();
        List<OrderDtos.LogisticsTrace> traces = new ArrayList<>();
        if (shippedAt != null) {
            addTrace(traces, now, shippedAt, "快件已由顺丰速运揽收");
            addTrace(traces, now, shippedAt.plusHours(6), "快件已到达杭州转运中心");
            addTrace(traces, now, shippedAt.plusHours(18), "快件已从杭州转运中心发出");
            addTrace(traces, now, shippedAt.plusHours(30), "快件正在派送，请保持电话畅通");
            if (order.getStatus() != null && order.getStatus() == OrderStatus.COMPLETED.getCode()) {
                addTrace(traces, now, shippedAt.plusHours(36), "快件已签收，感谢使用顺丰速运");
            }
            // 最新节点在前
            traces.sort((a, b) -> b.time().compareTo(a.time()));
        }

        String status = logisticsStatus(order, shippedAt, now);
        return new OrderDtos.LogisticsView(
                orderNo,
                lg.getLogisticsNo(),
                lg.getCarrier(),
                status,
                logisticsStatusText(status),
                traces);
    }

    /** 契约枚举：SHIPPED 已发货（12 小时内）/ IN_TRANSIT 运输中 / SIGNED 已签收（订单完成）。 */
    private String logisticsStatus(Order order, LocalDateTime shippedAt, LocalDateTime now) {
        if (order.getStatus() != null && order.getStatus() == OrderStatus.COMPLETED.getCode()) {
            return LOGISTICS_SIGNED;
        }
        if (shippedAt == null) {
            return LOGISTICS_SHIPPED;
        }
        return shippedAt.plusHours(12).isAfter(now) ? LOGISTICS_SHIPPED : LOGISTICS_IN_TRANSIT;
    }

    private String logisticsStatusText(String status) {
        return switch (status) {
            case LOGISTICS_SIGNED -> "已签收";
            case LOGISTICS_IN_TRANSIT -> "运输中";
            default -> "已发货";
        };
    }

    /** 只展示已经发生的节点，避免出现“未来时间”的轨迹。 */
    private void addTrace(List<OrderDtos.LogisticsTrace> traces, LocalDateTime now,
                          LocalDateTime nodeTime, String desc) {
        if (!nodeTime.isAfter(now)) {
            traces.add(new OrderDtos.LogisticsTrace(TimeUtil.format(nodeTime), desc));
        }
    }

    /** 物流单号必填（40972），并限制 6~32 位字母/数字/短横线。 */
    private String requireLogisticsNo(OrderDtos.ShipRequest req) {
        String value = req == null || req.logisticsNo() == null ? "" : req.logisticsNo().trim();
        if (value.isEmpty()) {
            throw new BizException(ErrorCode.LOGISTICS_NO_EMPTY);
        }
        if (value.length() > 32 || !value.matches("^[A-Za-z0-9-]{6,32}$")) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "物流单号格式不正确（6~32 位字母、数字或短横线）");
        }
        return value;
    }

    /** 快递公司必填（40972），如「顺丰速运」。 */
    private String requireCarrier(OrderDtos.ShipRequest req) {
        String value = req == null || req.carrier() == null ? "" : req.carrier().trim();
        if (value.isEmpty()) {
            throw new BizException(ErrorCode.LOGISTICS_NO_EMPTY);
        }
        if (value.length() > 32) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "快递公司名称不能超过 32 字");
        }
        return value;
    }

    /** 客户确认收货：40 已发货 → 50 交易完成，仅买家本人可操作，系统不做超时自动确认。 */
    @Transactional
    public OrderDtos.StatusView receive(String orderNo, Long customerId) {
        if (customerId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Order order = lockOrder(orderNo);
        if (!Objects.equals(order.getCustomerId(), customerId)) {
            throw new BizException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        int current = order.getStatus();
        if (OrderStatus.isFinal(current)) {
            throw new BizException(ErrorCode.ORDER_FINALIZED);
        }
        if (current != OrderStatus.SHIPPED.getCode()) {
            throw new BizException(ErrorCode.ORDER_STATUS_DENIED,
                    "只有「已发货」的订单才能确认收货",
                    new OrderDtos.StatusConflict(current, OrderStatus.SHIPPED.getCode()));
        }
        if (orderMapper.casStatus(orderNo, OrderStatus.SHIPPED.getCode(), OrderStatus.COMPLETED.getCode()) != 1) {
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return new OrderDtos.StatusView(orderNo, OrderStatus.COMPLETED.getCode(),
                OrderStatus.COMPLETED.getText());
    }

    /**
     * 支付超时自动取消，由 OrderTimeoutJob 调用。
     * 只处理仍处于 15 待支付的订单，重复执行安全。
     */
    @Transactional
    public boolean cancelExpired(String orderNo) {
        Order order = orderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null || order.getStatus() == null
                || order.getStatus() != OrderStatus.WAIT_PAY.getCode()) {
            return false;
        }
        restoreStock(order);
        return orderMapper.casStatus(orderNo, OrderStatus.WAIT_PAY.getCode(),
                OrderStatus.CANCELLED.getCode()) == 1;
    }

    // ============ 查询 ============

    /** 我的订单：含已取消，按下单时间倒序分页。 */
    public OrderDtos.OrderPage myOrders(Long customerId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Order> result = orderMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getCustomerId, customerId)
                        .orderByDesc(Order::getCreatedAt)
                        .orderByDesc(Order::getId));
        return toPage(result, safePage, safeSize);
    }

    /** 某客户的订单记录：卖家不提供全局订单列表，只能按客户查。 */
    public OrderDtos.OrderPage customerOrders(Long customerId, int page, int size) {
        return myOrders(customerId, page, size);
    }

    /** 订单详情：买家只能看自己的订单，卖家可看全部。 */
    public OrderDtos.OrderView detail(String orderNo, Long actorId, boolean seller) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!seller && !Objects.equals(order.getCustomerId(), actorId)) {
            throw new BizException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return toView(order, orderItemMapper.selectByOrderId(order.getId()));
    }

    // ============ 私有辅助 ============

    /** 商家可取消：下单到已发货之间，交易完成后不可。 */
    private boolean isSellerCancellable(int status) {
        return status == OrderStatus.PENDING.getCode()
                || status == OrderStatus.WAIT_PAY.getCode()
                || status == OrderStatus.PAID.getCode()
                || status == OrderStatus.CONFIRMED.getCode()
                || status == OrderStatus.READY.getCode()
                || status == OrderStatus.SHIPPED.getCode();
    }

    /** 客户可取消：已发货之前，发货后只能等收货或走售后。 */
    private boolean isCustomerCancellable(int status) {
        return status == OrderStatus.PENDING.getCode()
                || status == OrderStatus.WAIT_PAY.getCode()
                || status == OrderStatus.PAID.getCode()
                || status == OrderStatus.CONFIRMED.getCode()
                || status == OrderStatus.READY.getCode();
    }

    /** 按明细回补可售库存；库存从 0 回补时把因售罄下架的商品恢复在售。 */
    private void restoreStock(Order order) {
        for (OrderItem item : orderItemMapper.selectByOrderId(order.getId())) {
            productMapper.restoreStock(item.getProductId(), item.getQuantity());
            productMapper.restoreOnSaleIfSoldOut(item.getProductId());
        }
    }

    private Order lockOrder(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        Order order = orderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /** 入参校验与同商品合并：同一次下单里重复出现同一商品时按数量累加。 */
    private Map<Long, Integer> mergeItems(List<OrderDtos.CreateItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.ORDER_EMPTY);
        }
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderDtos.CreateItem item : items) {
            if (item == null || item.productId() == null || item.productId().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "商品 ID 不能为空");
            }
            Long productId;
            try {
                productId = Long.valueOf(item.productId().trim());
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "商品 ID 不合法：" + item.productId());
            }
            int quantity = item.quantity() == null ? 0 : item.quantity();
            if (quantity <= 0) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "购买数量必须大于 0");
            }
            if (quantity > MAX_QUANTITY) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "单件商品数量不能超过 " + MAX_QUANTITY);
            }
            merged.merge(productId, quantity, Integer::sum);
        }
        if (merged.size() > MAX_ITEMS) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "一次最多下单 " + MAX_ITEMS + " 种商品");
        }
        for (Integer quantity : merged.values()) {
            if (quantity > MAX_QUANTITY) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "单件商品数量不能超过 " + MAX_QUANTITY);
            }
        }
        return merged;
    }

    /** 生成 yyyyMMdd-#### 订单号；跨商品并发冲突由 uk_order_no 兜底为 40901。 */
    private String nextOrderNo() {
        String prefix = LocalDate.now().format(DAY);
        String max = orderMapper.selectMaxOrderNoOfDay(prefix);
        int seq = 1;
        if (max != null && max.length() > prefix.length() + 1) {
            try {
                seq = Integer.parseInt(max.substring(prefix.length() + 1)) + 1;
            } catch (NumberFormatException ignored) {
                seq = 1;
            }
        }
        return prefix + "-" + String.format("%04d", seq);
    }

    private OrderDtos.OrderPage toPage(Page<Order> result, int page, int size) {
        List<Order> records = result.getRecords();
        Map<Long, List<OrderItem>> itemMap = new LinkedHashMap<>();
        if (!records.isEmpty()) {
            List<Long> ids = records.stream().map(Order::getId).toList();
            itemMap = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                            .in(OrderItem::getOrderId, ids).orderByAsc(OrderItem::getId))
                    .stream()
                    .collect(Collectors.groupingBy(OrderItem::getOrderId, LinkedHashMap::new,
                            Collectors.mapping(Function.identity(), Collectors.toList())));
        }
        List<OrderDtos.OrderView> list = new ArrayList<>(records.size());
        for (Order order : records) {
            list.add(toView(order, itemMap.getOrDefault(order.getId(), List.of())));
        }
        return new OrderDtos.OrderPage(list, result.getTotal(), page, size);
    }

    private OrderDtos.OrderView toView(Order order, List<OrderItem> items) {
        List<OrderDtos.ItemView> itemViews = items.stream()
                .map(it -> new OrderDtos.ItemView(
                        String.valueOf(it.getProductId()),
                        it.getProductName(),
                        money(it.getPrice()),
                        it.getQuantity(),
                        money(it.getSubtotal())))
                .toList();
        int itemCount = items.stream().mapToInt(OrderItem::getQuantity).sum();
        return new OrderDtos.OrderView(
                order.getOrderNo(),
                order.getStatus(),
                OrderStatus.textOf(order.getStatus()),
                OrderStatus.finalResultOf(order.getStatus()),
                money(order.getTotalAmount()),
                itemCount,
                TimeUtil.format(order.getCreatedAt()),
                TimeUtil.format(order.getPayDeadline()),
                TimeUtil.format(order.getPaidAt()),
                itemViews);
    }

    private String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 下单过程中的临时行数据，避免重复读商品。 */
    private static final class Line {
        private final Long productId;
        private final String name;
        private final BigDecimal price;
        private final int quantity;
        private final BigDecimal subtotal;

        private Line(Long productId, String name, BigDecimal price, int quantity) {
            this.productId = productId;
            this.name = name;
            this.price = price == null ? BigDecimal.ZERO : price;
            this.quantity = quantity;
            this.subtotal = this.price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
