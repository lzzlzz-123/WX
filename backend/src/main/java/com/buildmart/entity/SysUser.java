package com.buildmart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId
    private Long id;
    private String openid;
    private String unionid;
    private String nickname;
    private String phone;
    private String companyName;
    private String avatarUrl;
    private BigDecimal creditLimit;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
