package com.buildmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.buildmart.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
}
