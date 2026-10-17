package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.buildmart.common.PageResult;
import com.buildmart.entity.InvoiceRequest;
import com.buildmart.entity.InvoiceTitle;
import com.buildmart.entity.PurchaseOrder;
import com.buildmart.mapper.InvoiceRequestMapper;
import com.buildmart.mapper.InvoiceTitleMapper;
import com.buildmart.mapper.PurchaseOrderMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单开票申请。小程序对已支付订单发起申请（每单限一张，表上有唯一约束兜底），
 * 管理端开具或驳回。
 */
@Service
public class InvoiceRequestService {
    private final InvoiceRequestMapper requestMapper;
    private final InvoiceTitleMapper titleMapper;
    private final PurchaseOrderMapper orderMapper;
    private final AdminAuditService auditService;

    public InvoiceRequestService(InvoiceRequestMapper requestMapper, InvoiceTitleMapper titleMapper,
                                 PurchaseOrderMapper orderMapper, AdminAuditService auditService) {
        this.requestMapper = requestMapper;
        this.titleMapper = titleMapper;
        this.orderMapper = orderMapper;
        this.auditService = auditService;
    }

    /** 小程序端发起开票申请。仅已支付订单可申请，每单限一次。 */
    public InvoiceRequest apply(Long userId, Long orderId, Long titleId, String remark) {
        PurchaseOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, orderId).eq(PurchaseOrder::getUserId, userId));
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if (!List.of(MiniappOrderService.ST_PENDING_SHIPMENT, MiniappOrderService.ST_PENDING_RECEIPT,
                MiniappOrderService.ST_COMPLETED).contains(order.getStatus())) {
            throw new IllegalStateException("仅已支付订单可申请开票");
        }
        Long exists = requestMapper.selectCount(new LambdaQueryWrapper<InvoiceRequest>()
                .eq(InvoiceRequest::getOrderId, orderId)
                .ne(InvoiceRequest::getStatus, "REJECTED"));
        if (exists != null && exists > 0) throw new IllegalStateException("该订单已提交过开票申请");
        InvoiceTitle title = titleMapper.selectOne(new LambdaQueryWrapper<InvoiceTitle>()
                .eq(InvoiceTitle::getId, titleId).eq(InvoiceTitle::getUserId, userId));
        if (title == null) throw new IllegalArgumentException("发票抬头不存在，请先维护抬头信息");

        InvoiceRequest request = new InvoiceRequest();
        request.setOrderId(orderId);
        request.setUserId(userId);
        request.setTitleId(titleId);
        request.setAmount(order.getPayableAmount());
        request.setStatus("PENDING");
        request.setRemark(remark);
        try {
            requestMapper.insert(request);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new IllegalStateException("该订单已提交过开票申请");
        }
        return request;
    }

    /** 小程序端查询当前用户的开票申请。 */
    public PageResult<InvoiceRequest> pageForUser(Long userId, long page, long size) {
        Page<InvoiceRequest> result = requestMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<InvoiceRequest>()
                        .eq(InvoiceRequest::getUserId, userId)
                        .orderByDesc(InvoiceRequest::getId));
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 管理端分页查询。 */
    public PageResult<InvoiceRequest> page(long page, long size, String status) {
        Page<InvoiceRequest> result = requestMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<InvoiceRequest>()
                        .eq(status != null && !status.isBlank(), InvoiceRequest::getStatus, status)
                        .orderByDesc(InvoiceRequest::getId));
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 管理端开具发票。 */
    public void issue(Long id) {
        if (requestMapper.updateStatus(id, "ISSUED", null) != 1) {
            throw new IllegalStateException("申请不存在或已处理");
        }
        auditService.record(com.buildmart.common.AuthContext.getAdminId(), null, "INVOICE_ISSUE", String.valueOf(id), null);
    }

    /** 管理端驳回申请。 */
    public void reject(Long id, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("请填写驳回原因");
        if (requestMapper.updateStatus(id, "REJECTED", reason) != 1) {
            throw new IllegalStateException("申请不存在或已处理");
        }
        auditService.record(com.buildmart.common.AuthContext.getAdminId(), null, "INVOICE_REJECT", String.valueOf(id), reason);
    }
}
