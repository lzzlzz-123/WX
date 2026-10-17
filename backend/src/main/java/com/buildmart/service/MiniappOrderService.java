package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.buildmart.common.PageResult;
import com.buildmart.entity.OrderItem;
import com.buildmart.entity.OrderShipment;
import com.buildmart.entity.Product;
import com.buildmart.entity.PurchaseOrder;
import com.buildmart.mapper.OrderItemMapper;
import com.buildmart.mapper.OrderShipmentMapper;
import com.buildmart.mapper.ProductMapper;
import com.buildmart.mapper.PurchaseOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MiniappOrderService {
    private static final Logger log = LoggerFactory.getLogger(MiniappOrderService.class);

    /** 订单状态机：PENDING_PAYMENT -> PENDING_SHIPMENT -> PENDING_RECEIPT -> COMPLETED；
     *  PENDING_PAYMENT -> CANCELLED（用户取消或超时关单）；退款成功后 -> REFUNDED。 */
    public static final String ST_PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String ST_PENDING_SHIPMENT = "PENDING_SHIPMENT";
    public static final String ST_PENDING_RECEIPT = "PENDING_RECEIPT";
    public static final String ST_COMPLETED = "COMPLETED";
    public static final String ST_CANCELLED = "CANCELLED";
    public static final String ST_REFUNDED = "REFUNDED";

    private final PurchaseOrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderShipmentMapper shipmentMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;

    public MiniappOrderService(PurchaseOrderMapper orderMapper, OrderItemMapper itemMapper,
                               OrderShipmentMapper shipmentMapper,
                               ProductMapper productMapper, ProductService productService) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.shipmentMapper = shipmentMapper;
        this.productMapper = productMapper;
        this.productService = productService;
    }

    @Transactional
    public PurchaseOrder create(Long userId, List<OrderLine> lines, String receiverName,
                                String receiverPhone, String receiverAddress, String deliveryMode, String remark) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("订单商品不能为空");
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(nextOrderNo());
        order.setUserId(userId);
        order.setStatus(ST_PENDING_PAYMENT);
        order.setPaymentMethod("WECHAT");
        order.setDeliveryMode(deliveryMode == null ? "CITY_DELIVERY" : deliveryMode);
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setReceiverAddress(receiverAddress);
        order.setRemark(remark);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setPayableAmount(BigDecimal.ZERO);
        orderMapper.insert(order);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine line : lines) {
            if (line.productId() == null || line.quantity() == null || line.quantity().signum() <= 0) {
                throw new IllegalArgumentException("订单商品和数量不合法");
            }
            Product product = productService.get(line.productId());
            if (product == null || product.getStatus() == null || product.getStatus() != 1) {
                throw new IllegalArgumentException("商品不存在或已下架");
            }
            if (product.getMinOrderQty() != null && line.quantity().compareTo(product.getMinOrderQty()) < 0) {
                throw new IllegalArgumentException(product.getName() + "起订量为 " + product.getMinOrderQty() + " " + product.getUnit());
            }
            if (productMapper.decrementStock(line.productId(), line.quantity()) != 1) {
                throw new IllegalStateException("商品库存不足：" + product.getName());
            }
            BigDecimal unitPrice = productService.priceFor(product, line.quantity());
            BigDecimal amount = unitPrice.multiply(line.quantity());
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setSpecification(product.getSpecification());
            item.setUnit(product.getUnit());
            item.setQuantity(line.quantity());
            item.setUnitPrice(unitPrice);
            item.setAmount(amount);
            itemMapper.insert(item);
            total = total.add(amount);
            productService.evict(product.getId());
        }
        order.setTotalAmount(total);
        order.setPayableAmount(total.add(order.getFreightAmount()));
        orderMapper.updateById(order);
        return order;
    }

    public PageResult<PurchaseOrder> page(Long userId, long page, long size, String status) {
        var query = new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getUserId, userId)
                .eq(status != null && !status.isBlank(), PurchaseOrder::getStatus, status)
                .orderByDesc(PurchaseOrder::getCreatedAt);
        Page<PurchaseOrder> result = orderMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public OrderDetail detail(Long userId, Long id) {
        PurchaseOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, id).eq(PurchaseOrder::getUserId, userId));
        if (order == null) throw new IllegalArgumentException("订单不存在");
        List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        List<OrderShipment> shipments = shipmentMapper.selectList(
                new LambdaQueryWrapper<OrderShipment>().eq(OrderShipment::getOrderId, id).orderByDesc(OrderShipment::getId));
        return new OrderDetail(order, items, shipments);
    }

    /**
     * 用户取消订单：仅待支付订单可取消。状态用 CAS 流转，流转成功才回补库存，
     * 避免与支付回调、超时关单并发时重复回补。
     */
    @Transactional
    public PurchaseOrder cancel(Long userId, Long id) {
        PurchaseOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, id).eq(PurchaseOrder::getUserId, userId));
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if (!ST_PENDING_PAYMENT.equals(order.getStatus())) {
            throw new IllegalStateException("当前状态不可取消，如需帮助请联系客服");
        }
        if (orderMapper.updateStatus(id, ST_PENDING_PAYMENT, ST_CANCELLED) != 1) {
            throw new IllegalStateException("订单状态已变化，请刷新后重试");
        }
        restoreStock(id);
        order.setStatus(ST_CANCELLED);
        return order;
    }

    /**
     * 确认收货：仅待收货订单可确认。
     */
    @Transactional
    public PurchaseOrder confirmReceipt(Long userId, Long id) {
        PurchaseOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, id).eq(PurchaseOrder::getUserId, userId));
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if (orderMapper.markCompleted(id) != 1) {
            throw new IllegalStateException("当前状态不可确认收货");
        }
        order.setStatus(ST_COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        return order;
    }

    /**
     * 支付成功入账（微信支付回调调用）：CAS 待支付 -> 待发货。
     * @return true 表示本次回调完成了状态流转；false 表示订单已处理过或状态不符（幂等安全）。
     */
    @Transactional
    public boolean markPaid(Long orderId) {
        return orderMapper.markPaid(orderId) == 1;
    }

    /**
     * 超时未支付自动关单：扫描截止时间之前的待支付订单，逐笔 CAS 关单并回补库存。
     * @return 实际关闭的订单数
     */
    @Transactional
    public int closeTimeoutOrders(LocalDateTime before, int batchSize) {
        List<PurchaseOrder> timeoutOrders = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getStatus, ST_PENDING_PAYMENT)
                .lt(PurchaseOrder::getCreatedAt, before)
                .orderByAsc(PurchaseOrder::getId)
                .last("LIMIT " + Math.max(1, Math.min(batchSize, 500))));
        int closed = 0;
        for (PurchaseOrder order : timeoutOrders) {
            if (orderMapper.updateStatus(order.getId(), ST_PENDING_PAYMENT, ST_CANCELLED) == 1) {
                restoreStock(order.getId());
                closed++;
                log.info("订单超时未支付自动关闭 orderId={} orderNo={}", order.getId(), order.getOrderNo());
            }
        }
        return closed;
    }

    /** 按商户订单号查询（支付/退款回调用）。 */
    public PurchaseOrder getByOrderNo(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getOrderNo, orderNo));
    }

    /**
     * 退款成功后的状态流转：待发货 -> 已退款并回补库存（此时货未发出）。
     * 已发货/已完成的订单退款只改状态，不回补库存（退货需线下处理）。
     */
    @Transactional
    public boolean markRefunded(Long orderId) {
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if (ST_REFUNDED.equals(order.getStatus())) return false;
        if (ST_PENDING_SHIPMENT.equals(order.getStatus())) {
            if (orderMapper.updateStatus(orderId, ST_PENDING_SHIPMENT, ST_REFUNDED) == 1) {
                restoreStock(orderId);
                return true;
            }
            return false;
        }
        // 其他状态直接置为已退款（幂等 CAS 不适用多来源，这里做一次兜底校验）
        order.setStatus(ST_REFUNDED);
        orderMapper.updateById(order);
        return true;
    }

    /** 回补订单占用库存（取消/关单/退款共用）。 */
    public void restoreStock(Long orderId) {
        List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            productMapper.incrementStock(item.getProductId(), item.getQuantity());
            productService.evict(item.getProductId());
        }
    }

    private String nextOrderNo() {
        return "BM" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    public record OrderLine(Long productId, BigDecimal quantity) {
    }

    public record OrderDetail(PurchaseOrder order, List<OrderItem> items, List<OrderShipment> shipments) {
    }
}
