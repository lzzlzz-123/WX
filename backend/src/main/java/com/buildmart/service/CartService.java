package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.buildmart.common.CacheKeys;
import com.buildmart.entity.CartItem;
import com.buildmart.entity.Product;
import com.buildmart.mapper.CartItemMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Service
public class CartService {
    /** Cart reads are cached briefly; every mutation evicts before returning. */
    private static final Duration CART_TTL = Duration.ofSeconds(60);

    private final CartItemMapper cartMapper;
    private final ProductService productService;
    private final RedisCacheService cacheService;
    private final ObjectMapper objectMapper;

    public CartService(CartItemMapper cartMapper, ProductService productService,
                       RedisCacheService cacheService, ObjectMapper objectMapper) {
        this.cartMapper = cartMapper;
        this.productService = productService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    public List<CartItem> list(Long userId) {
        String key = CacheKeys.cart(userId);
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, CartItem.class);
        List<CartItem> cached = cacheService.get(key, listType);
        if (cached != null) return cached;

        List<CartItem> items = cartMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getUpdatedAt));
        cacheService.put(key, items, CART_TTL);
        return items;
    }

    public CartItem add(Long userId, Long productId, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("采购数量必须大于 0");
        Product product = productService.get(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) throw new IllegalArgumentException("商品不存在或已下架");
        CartItem item = cartMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId).eq(CartItem::getProductId, productId));
        if (item == null) {
            item = new CartItem();
            item.setUserId(userId);
            item.setProductId(productId);
            item.setQuantity(quantity);
            item.setSelected(1);
            cartMapper.insert(item);
        } else {
            item.setQuantity(item.getQuantity().add(quantity));
            cartMapper.updateById(item);
        }
        evict(userId);
        return item;
    }

    public void update(Long userId, Long id, BigDecimal quantity, Integer selected) {
        CartItem item = ownedItem(userId, id);
        if (quantity != null) {
            if (quantity.signum() <= 0) throw new IllegalArgumentException("采购数量必须大于 0");
            item.setQuantity(quantity);
        }
        if (selected != null) item.setSelected(selected == 1 ? 1 : 0);
        cartMapper.updateById(item);
        evict(userId);
    }

    public void delete(Long userId, Long id) {
        cartMapper.delete(new LambdaQueryWrapper<CartItem>().eq(CartItem::getId, id).eq(CartItem::getUserId, userId));
        evict(userId);
    }

    /** Called by order creation after checked-out items are removed. */
    public void evict(Long userId) {
        cacheService.delete(CacheKeys.cart(userId));
    }

    private CartItem ownedItem(Long userId, Long id) {
        CartItem item = cartMapper.selectOne(new LambdaQueryWrapper<CartItem>().eq(CartItem::getId, id).eq(CartItem::getUserId, userId));
        if (item == null) throw new IllegalArgumentException("购物车商品不存在");
        return item;
    }
}
