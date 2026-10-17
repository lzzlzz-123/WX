package com.buildmart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("invoice_request")
public class InvoiceRequest {
    @TableId
    private Long id;
    private Long orderId;
    private Long userId;
    private Long titleId;
    private BigDecimal amount;
    /** PENDING 待开具 / ISSUED 已开具 / REJECTED 已驳回 */
    private String status;
    private String rejectReason;
    private LocalDateTime issuedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
