package com.buildmart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_record")
public class PaymentRecord {
    public static final String TYPE_PAY = "PAY";
    public static final String TYPE_REFUND = "REFUND";

    @TableId
    private Long id;
    private Long orderId;
    private String orderNo;
    /** PAY / REFUND */
    private String type;
    /** 微信支付交易号（PAY）或微信退款单号（REFUND） */
    private String transactionId;
    /** 商户退款单号（REFUND 时唯一） */
    private String outRefundNo;
    private BigDecimal amount;
    /** SUCCESS / PROCESSING / FAILED / CLOSED */
    private String status;
    /** 回调原文，便于对账排查 */
    private String rawNotify;
    private LocalDateTime createdAt;
}
