package com.buildmart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_operation_log")
public class AdminOperationLog {
    @TableId
    private Long id;
    private Long adminId;
    private String adminUsername;
    /** 操作类型，如 LOGIN / ACCOUNT_CREATE / ORDER_SHIP / ORDER_REFUND / STOCK_ADJUST / SETTING_SAVE */
    private String action;
    /** 操作对象，如订单号、商品 SKU */
    private String target;
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
