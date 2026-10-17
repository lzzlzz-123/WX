package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.buildmart.common.PageResult;
import com.buildmart.entity.OrderShipment;
import com.buildmart.entity.PurchaseOrder;
import com.buildmart.mapper.OrderShipmentMapper;
import com.buildmart.mapper.PurchaseOrderMapper;
import com.buildmart.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {
    private final PurchaseOrderMapper orderMapper;
    private final OrderShipmentMapper shipmentMapper;
    private final WxPayService wxPayService;
    private final SysUserMapper userMapper;
    private final WxSubscribeMessageService subscribeMessageService;
    private final AdminAuditService auditService;

    public OrderService(PurchaseOrderMapper orderMapper, OrderShipmentMapper shipmentMapper,
                        WxPayService wxPayService, SysUserMapper userMapper,
                        WxSubscribeMessageService subscribeMessageService, AdminAuditService auditService) {
        this.orderMapper = orderMapper;
        this.shipmentMapper = shipmentMapper;
        this.wxPayService = wxPayService;
        this.userMapper = userMapper;
        this.subscribeMessageService = subscribeMessageService;
        this.auditService = auditService;
    }

    public PageResult<PurchaseOrder> page(long page, long size, String status) {
        var query = new LambdaQueryWrapper<PurchaseOrder>()
                .eq(status != null && !status.isBlank(), PurchaseOrder::getStatus, status)
                .orderByDesc(PurchaseOrder::getCreatedAt);
        Page<PurchaseOrder> result = orderMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 发货：CAS 待发货 -> 待收货，并落一条物流记录。
     * 自提订单（SELF_PICKUP）生成 6 位核销码，用户凭码提货，无需物流单号。
     */
    @Transactional
    public OrderShipment ship(Long id, String logisticsCompany, String trackingNo, String remark) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        boolean selfPickup = "SELF_PICKUP".equals(order.getDeliveryMode()) || "PICKUP".equals(order.getDeliveryMode());
        if (!selfPickup && (logisticsCompany == null || logisticsCompany.isBlank()
                || trackingNo == null || trackingNo.isBlank())) {
            throw new IllegalArgumentException("配送订单必须填写物流公司和运单号");
        }
        if (orderMapper.updateStatus(id, MiniappOrderService.ST_PENDING_SHIPMENT,
                MiniappOrderService.ST_PENDING_RECEIPT) != 1) {
            throw new IllegalStateException("当前订单状态不可发货");
        }
        OrderShipment shipment = new OrderShipment();
        shipment.setOrderId(id);
        shipment.setDeliveryMode(order.getDeliveryMode());
        shipment.setLogisticsCompany(selfPickup ? null : logisticsCompany);
        shipment.setTrackingNo(selfPickup ? null : trackingNo);
        shipment.setPickupCode(selfPickup ? nextPickupCode() : null);
        shipment.setRemark(remark);
        shipmentMapper.insert(shipment);
        auditService.record(com.buildmart.common.AuthContext.getAdminId(), null, "ORDER_SHIP", order.getOrderNo(),
                selfPickup ? "自提发货，核销码已生成" : logisticsCompany + " " + trackingNo);
        // 发货订阅消息（模板未配置或推送失败均不影响发货主流程）
        try {
            com.buildmart.entity.SysUser user = userMapper.selectById(order.getUserId());
            if (user != null) {
                String info = selfPickup ? "自提核销码 " + shipment.getPickupCode()
                        : logisticsCompany + " " + trackingNo;
                subscribeMessageService.notifyOrderShipped(user.getOpenid(), order.getOrderNo(), info);
            }
        } catch (Exception ex) {
            // 推送失败不影响主流程
        }
        return shipment;
    }

    /**
     * 退款：发起微信退款（原路退回），退款成功由退款回调异步入账。
     * 仅限待发货订单；已发货订单的退货退款需线下处理。
     */
    public void refund(Long id, BigDecimal amount, String reason) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        BigDecimal refundAmount = (amount == null || amount.signum() <= 0) ? order.getPayableAmount() : amount;
        if (refundAmount.compareTo(order.getPayableAmount()) > 0) {
            throw new IllegalArgumentException("退款金额不能超过实付金额");
        }
        wxPayService.refund(order, refundAmount, reason);
        auditService.record(com.buildmart.common.AuthContext.getAdminId(), null, "ORDER_REFUND",
                order.getOrderNo(), "退款 ¥" + refundAmount + "，原因：" + reason);
    }

    private String nextPickupCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }
}
