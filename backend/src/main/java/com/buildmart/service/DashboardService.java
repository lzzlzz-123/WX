package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.buildmart.entity.Inquiry;
import com.buildmart.entity.Product;
import com.buildmart.entity.PurchaseOrder;
import com.buildmart.mapper.InquiryMapper;
import com.buildmart.mapper.ProductMapper;
import com.buildmart.mapper.PurchaseOrderMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class DashboardService {
    private final ProductMapper productMapper;
    private final PurchaseOrderMapper orderMapper;
    private final InquiryMapper inquiryMapper;

    public DashboardService(ProductMapper productMapper, PurchaseOrderMapper orderMapper,
                            InquiryMapper inquiryMapper) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.inquiryMapper = inquiryMapper;
    }

    public DashboardSummary summary() {
        long activeProductCount = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1));
        long lowStockCount = productMapper.selectCount(new QueryWrapper<Product>()
                .eq("status", 1)
                .apply("stock <= min_order_qty"));
        long orderCount = orderMapper.selectCount(new QueryWrapper<PurchaseOrder>());
        long pendingShipmentCount = orderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getStatus, "PENDING_SHIPMENT"));
        long pendingInquiryCount = inquiryMapper.selectCount(new LambdaQueryWrapper<Inquiry>()
                .eq(Inquiry::getStatus, "PENDING_QUOTE"));
        BigDecimal orderAmount = sumPayableAmount();
        return new DashboardSummary(activeProductCount, lowStockCount, orderCount,
                pendingShipmentCount, pendingInquiryCount, orderAmount);
    }

    private BigDecimal sumPayableAmount() {
        Object value = orderMapper.selectObjs(new QueryWrapper<PurchaseOrder>()
                .select("COALESCE(SUM(payable_amount), 0) AS payable_amount"))
                .stream()
                .findFirst()
                .orElse(null);
        if (value == null) return BigDecimal.ZERO;
        try {
            return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    public record DashboardSummary(
            long activeProductCount,
            long lowStockCount,
            long orderCount,
            long pendingShipmentCount,
            long pendingInquiryCount,
            BigDecimal orderAmount) {
    }
}
