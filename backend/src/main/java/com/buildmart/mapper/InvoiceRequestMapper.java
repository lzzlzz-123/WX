package com.buildmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.buildmart.entity.InvoiceRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InvoiceRequestMapper extends BaseMapper<InvoiceRequest> {

    /** 开具/驳回的 CAS 流转：仅待处理申请可流转。 */
    @Update("UPDATE invoice_request SET status = #{toStatus}, issued_at = CASE WHEN #{toStatus} = 'ISSUED' THEN NOW() ELSE NULL END, " +
            "reject_reason = #{rejectReason} WHERE id = #{id} AND status = 'PENDING'")
    int updateStatus(@Param("id") Long id, @Param("toStatus") String toStatus, @Param("rejectReason") String rejectReason);
}
