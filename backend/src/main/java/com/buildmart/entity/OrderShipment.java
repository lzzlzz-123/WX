package com.buildmart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_shipment")
public class OrderShipment {
    @TableId
    private Long id;
    private Long orderId;
    /** 配送方式：CITY_DELIVERY 同城配送 / SELF_PICKUP 自提 / EXPRESS 快递物流 */
    private String deliveryMode;
    private String logisticsCompany;
    private String trackingNo;
    /** 自提订单的核销码 */
    private String pickupCode;
    private String remark;
    private LocalDateTime createdAt;
}
