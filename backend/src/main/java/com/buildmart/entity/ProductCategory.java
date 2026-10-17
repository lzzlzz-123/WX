package com.buildmart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("product_category")
public class ProductCategory {
    @TableId
    private Long id;
    private Long parentId;
    private String name;
    private String iconUrl;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime createdAt;
}
