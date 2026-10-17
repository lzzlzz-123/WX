package com.buildmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.buildmart.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {

    /** 状态机 CAS 流转：仅当前状态为 fromStatus 时才更新为 toStatus，防止并发重复流转。 */
    @Update("UPDATE purchase_order SET status = #{toStatus} WHERE id = #{id} AND status = #{fromStatus}")
    int updateStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);

    /** 支付成功：待支付 -> 待发货，并记录支付时间与微信交易号。 */
    @Update("UPDATE purchase_order SET status = 'PENDING_SHIPMENT', paid_at = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING_PAYMENT'")
    int markPaid(@Param("id") Long id);

    /** 确认收货：待收货 -> 已完成。 */
    @Update("UPDATE purchase_order SET status = 'COMPLETED', completed_at = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING_RECEIPT'")
    int markCompleted(@Param("id") Long id);
}
