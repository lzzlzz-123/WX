package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.buildmart.common.PageResult;
import com.buildmart.entity.PurchaseOrder;
import com.buildmart.mapper.PurchaseOrderMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OrderService {
    private final PurchaseOrderMapper orderMapper;

    public OrderService(PurchaseOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    public PageResult<PurchaseOrder> page(long page, long size, String status) {
        var query = new LambdaQueryWrapper<PurchaseOrder>()
                .eq(status != null && !status.isBlank(), PurchaseOrder::getStatus, status)
                .orderByDesc(PurchaseOrder::getCreatedAt);
        Page<PurchaseOrder> result = orderMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public void ship(Long id) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if (!"PENDING_SHIPMENT".equals(order.getStatus())) throw new IllegalStateException("当前订单状态不可发货");
        order.setStatus("PENDING_RECEIPT");
        order.setShippedAt(LocalDateTime.now());
        orderMapper.updateById(order);
    }
}
