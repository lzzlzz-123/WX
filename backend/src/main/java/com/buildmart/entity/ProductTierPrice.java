package com.buildmart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("product_tier_price")
public class ProductTierPrice {
    @TableId
    private Long id;
    private Long productId;
    private BigDecimal minQty;
    private BigDecimal price;
}
