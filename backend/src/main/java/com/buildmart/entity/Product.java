package com.buildmart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId
    private Long id;
    private Long categoryId;
    private String sku;
    private String name;
    private String brand;
    private String grade;
    private String specification;
    private String unit;
    private BigDecimal basePrice;
    private BigDecimal stock;
    private BigDecimal minOrderQty;
    private BigDecimal soldQty;
    private String deliveryModes;
    private String coverUrl;
    private String detail;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
