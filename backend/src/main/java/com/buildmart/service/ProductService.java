package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.buildmart.common.CacheKeys;
import com.buildmart.common.PageResult;
import com.buildmart.entity.Product;
import com.buildmart.entity.ProductTierPrice;
import com.buildmart.mapper.ProductMapper;
import com.buildmart.mapper.ProductTierPriceMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Service
public class ProductService {
    private final ProductMapper productMapper;
    private final ProductTierPriceMapper tierPriceMapper;
    private final RedisCacheService cacheService;
    private final ObjectMapper objectMapper;

    public ProductService(ProductMapper productMapper, ProductTierPriceMapper tierPriceMapper,
                          RedisCacheService cacheService, ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.tierPriceMapper = tierPriceMapper;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    public PageResult<Product> page(long page, long size, String keyword, Integer status) {
        long version = catalogVersion();
        String cacheKey = CacheKeys.productPage(version, page, size, keyword, status);
        JavaType pageType = objectMapper.getTypeFactory().constructParametricType(PageResult.class, Product.class);
        PageResult<Product> cached = cacheService.get(cacheKey, pageType);
        if (cached != null) return cached;

        var query = new LambdaQueryWrapper<Product>()
                .and(keyword != null && !keyword.isBlank(), q -> q.like(Product::getName, keyword).or().like(Product::getSku, keyword))
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getCreatedAt);
        Page<Product> result = productMapper.selectPage(new Page<>(page, size), query);
        PageResult<Product> pageResult = new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
        cacheService.put(cacheKey, pageResult, Duration.ofSeconds(60));
        return pageResult;
    }

    public Product get(Long id) {
        Product cached = cacheService.get(CacheKeys.productDetail(id), Product.class);
        if (cached != null) return cached;
        if (cacheService.hasKey(CacheKeys.productEmpty(id))) return null;

        Product product = productMapper.selectById(id);
        if (product == null) {
            // Short-lived negative cache prevents repeated misses from hammering MySQL.
            cacheService.putRaw(CacheKeys.productEmpty(id), "1", Duration.ofSeconds(30));
            return null;
        }
        cacheService.put(CacheKeys.productDetail(id), product, Duration.ofMinutes(10));
        return product;
    }

    public BigDecimal priceFor(Product product, BigDecimal quantity) {
        return tierPriceMapper.selectList(new LambdaQueryWrapper<ProductTierPrice>()
                        .eq(ProductTierPrice::getProductId, product.getId())
                        .le(ProductTierPrice::getMinQty, quantity)
                        .orderByDesc(ProductTierPrice::getMinQty))
                .stream()
                .findFirst()
                .map(ProductTierPrice::getPrice)
                .orElse(product.getBasePrice());
    }

    @Transactional
    public Product save(Product product, List<ProductTierPrice> tiers) {
        if (product.getStatus() == null) product.setStatus(1);
        if (product.getStock() == null) product.setStock(BigDecimal.ZERO);
        if (product.getId() == null) {
            productMapper.insert(product);
        } else {
            productMapper.updateById(product);
        }
        tierPriceMapper.delete(new LambdaQueryWrapper<ProductTierPrice>().eq(ProductTierPrice::getProductId, product.getId()));
        if (tiers != null) {
            tiers.forEach(tier -> {
                tier.setId(null);
                tier.setProductId(product.getId());
                tierPriceMapper.insert(tier);
            });
        }
        invalidate(product.getId());
        return product;
    }

    public void changeStatus(Long id, Integer status) {
        Product product = new Product();
        product.setId(id);
        product.setStatus(status);
        productMapper.updateById(product);
        invalidate(id);
    }

    public void evict(Long id) {
        invalidate(id);
    }

    private long catalogVersion() {
        String value = cacheService.getRaw(CacheKeys.PRODUCT_CATALOG_VERSION);
        if (value == null) {
            cacheService.putRaw(CacheKeys.PRODUCT_CATALOG_VERSION, "1", Duration.ofDays(30));
            return 1L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            cacheService.putRaw(CacheKeys.PRODUCT_CATALOG_VERSION, "1", Duration.ofDays(30));
            return 1L;
        }
    }

    private void invalidate(Long productId) {
        cacheService.delete(CacheKeys.productDetail(productId));
        cacheService.delete(CacheKeys.productEmpty(productId));
        cacheService.increment(CacheKeys.PRODUCT_CATALOG_VERSION);
    }
}
