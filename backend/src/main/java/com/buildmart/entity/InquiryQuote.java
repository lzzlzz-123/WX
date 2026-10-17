package com.buildmart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inquiry_quote")
public class InquiryQuote {
    @TableId
    private Long id;
    private Long inquiryId;
    private Long adminId;
    private BigDecimal quotePrice;
    private LocalDate validUntil;
    private BigDecimal deliveryFee;
    private String remark;
    private String status;
    private LocalDateTime createdAt;
}
