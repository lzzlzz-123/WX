package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.buildmart.common.PageResult;
import com.buildmart.entity.OrderItem;
import com.buildmart.entity.Product;
import com.buildmart.entity.PurchaseOrder;
import com.buildmart.mapper.OrderItemMapper;
import com.buildmart.mapper.ProductMapper;
import com.buildmart.mapper.PurchaseOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MiniappOrderService {
    private final PurchaseOrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;

    public MiniappOrderService(PurchaseOrderMapper orderMapper, OrderItemMapper itemMapper,
                               ProductMapper productMapper, ProductService productService) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
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
        order.setStatus("PENDING_PAYMENT");
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
        return new OrderDetail(order, items);
    }

    /**
     * 用户取消订单：仅待支付订单可取消，取消后回补库存。
     */
    @Transactional
    public PurchaseOrder cancel(Long userId, Long id) {
        PurchaseOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, id).eq(PurchaseOrder::getUserId, userId));
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new IllegalStateException("当前状态不可取消，如需帮助请联系客服");
        }
        List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        for (OrderItem item : items) {
            productMapper.incrementStock(item.getProductId(), item.getQuantity());
            productService.evict(item.getProductId());
        }
        order.setStatus("CANCELLED");
        orderMapper.updateById(order);
        return order;
    }

    private String nextOrderNo() {
        return "BM" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    public record OrderLine(Long productId, BigDecimal quantity) {
    }

    public record OrderDetail(PurchaseOrder order, List<OrderItem> items) {
    }
}
