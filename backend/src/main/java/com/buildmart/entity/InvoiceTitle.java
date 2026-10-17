package com.buildmart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("invoice_title")
public class InvoiceTitle {
    @TableId
    private Long id;
    private Long userId;
    /** PERSONAL 个人 / COMPANY 企业 */
    private String titleType;
    /** 发票抬头（个人姓名或企业名称） */
    private String title;
    /** 企业税号（企业抬头必填） */
    private String taxNo;
    private String bankName;
    private String bankAccount;
    private String registerAddress;
    private String registerPhone;
    /** 接收电子发票的邮箱 */
    private String email;
    private Integer isDefault;
    private LocalDateTime createdAt;
}
