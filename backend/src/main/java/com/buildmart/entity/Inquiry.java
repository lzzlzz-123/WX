package com.buildmart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inquiry")
public class Inquiry {
    @TableId
    private Long id;
    private String inquiryNo;
    private Long userId;
    private String productName;
    private String specification;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal expectedPrice;
    private String deliveryAddress;
    private LocalDate deadline;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
}
