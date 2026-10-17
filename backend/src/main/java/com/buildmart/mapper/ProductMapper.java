package com.buildmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.buildmart.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    @Update("UPDATE product SET stock = stock - #{quantity}, sold_qty = sold_qty + #{quantity} WHERE id = #{productId} AND stock >= #{quantity}")
    int decrementStock(@Param("productId") Long productId, @Param("quantity") java.math.BigDecimal quantity);

    /** 订单取消时回补库存。 */
    @Update("UPDATE product SET stock = stock + #{quantity}, sold_qty = GREATEST(sold_qty - #{quantity}, 0) WHERE id = #{productId}")
    int incrementStock(@Param("productId") Long productId, @Param("quantity") java.math.BigDecimal quantity);

    /** 库存盘点调整：增加（不影响销量）。 */
    @Update("UPDATE product SET stock = stock + #{quantity} WHERE id = #{productId}")
    int addStock(@Param("productId") Long productId, @Param("quantity") java.math.BigDecimal quantity);

    /** 库存盘点调整：减少，库存不足时失败。 */
    @Update("UPDATE product SET stock = stock - #{quantity} WHERE id = #{productId} AND stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") java.math.BigDecimal quantity);
}
