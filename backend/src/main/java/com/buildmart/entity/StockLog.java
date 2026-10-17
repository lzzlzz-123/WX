package com.buildmart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("stock_log")
public class StockLog {
    @TableId
    private Long id;
    private Long productId;
    /** 调整量，正数为入库，负数为出库 */
    private BigDecimal delta;
    private BigDecimal stockAfter;
    private String reason;
    private Long operatorAdminId;
    private LocalDateTime createdAt;
}
