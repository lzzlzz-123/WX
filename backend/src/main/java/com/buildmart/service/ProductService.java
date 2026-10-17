package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.buildmart.common.CacheKeys;
import com.buildmart.common.PageResult;
import com.buildmart.entity.Product;
import com.buildmart.entity.ProductTierPrice;
import com.buildmart.entity.StockLog;
import com.buildmart.mapper.ProductMapper;
import com.buildmart.mapper.ProductTierPriceMapper;
import com.buildmart.mapper.StockLogMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Service
public class ProductService {
    private final ProductMapper productMapper;
    private final ProductTierPriceMapper tierPriceMapper;
    private final StockLogMapper stockLogMapper;
    private final SettingService settingService;
    private final AdminAuditService auditService;
    private final RedisCacheService cacheService;
    private final ObjectMapper objectMapper;

    public ProductService(ProductMapper productMapper, ProductTierPriceMapper tierPriceMapper,
                          StockLogMapper stockLogMapper, SettingService settingService,
                          AdminAuditService auditService,
                          RedisCacheService cacheService, ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.tierPriceMapper = tierPriceMapper;
        this.stockLogMapper = stockLogMapper;
        this.settingService = settingService;
        this.auditService = auditService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    public PageResult<Product> page(long page, long size, String keyword, Integer status) {
        return page(page, size, keyword, null, null, status);
    }

    /** 分页查询：keyword 模糊匹配名称/SKU/规格，brand 与 categoryId 精确筛选。 */
    public PageResult<Product> page(long page, long size, String keyword, String brand, Long categoryId, Integer status) {
        long version = catalogVersion();
        String cacheKey = CacheKeys.productPage(version, page, size,
                (keyword == null ? "" : keyword) + "|" + (brand == null ? "" : brand) + "|" + (categoryId == null ? "" : categoryId), status);
        JavaType pageType = objectMapper.getTypeFactory().constructParametricType(PageResult.class, Product.class);
        PageResult<Product> cached = cacheService.get(cacheKey, pageType);
        if (cached != null) return cached;

        var query = new LambdaQueryWrapper<Product>()
                .and(keyword != null && !keyword.isBlank(), q -> q.like(Product::getName, keyword)
                        .or().like(Product::getSku, keyword)
                        .or().like(Product::getSpecification, keyword))
                .eq(brand != null && !brand.isBlank(), Product::getBrand, brand)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getCreatedAt);
        Page<Product> result = productMapper.selectPage(new Page<>(page, size), query);
        PageResult<Product> pageResult = new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
        cacheService.put(cacheKey, pageResult, Duration.ofSeconds(60));
        return pageResult;
    }

    /** 在售商品的品牌列表（小程序筛选用）。 */
    public List<String> brands() {
        return productMapper.selectObjs(new LambdaQueryWrapper<Product>()
                        .select(Product::getBrand)
                        .eq(Product::getStatus, 1)
                        .isNotNull(Product::getBrand)
                        .ne(Product::getBrand, "")
                        .groupBy(Product::getBrand))
                .stream().map(String::valueOf).toList();
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

    /** 批量上下架。 */
    @Transactional
    public int batchChangeStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("商品 ID 列表不能为空");
        if (status == null || (status != 0 && status != 1)) throw new IllegalArgumentException("状态值不合法");
        int changed = 0;
        for (Long id : ids) {
            Product product = new Product();
            product.setId(id);
            product.setStatus(status);
            if (productMapper.updateById(product) == 1) changed++;
            invalidate(id);
        }
        return changed;
    }

    /**
     * 库存盘点调整：正数入库、负数出库（不足则失败），并记录操作流水。
     */
    @Transactional
    public StockLog adjustStock(Long id, BigDecimal delta, String reason, Long operatorAdminId) {
        if (delta == null || delta.signum() == 0) throw new IllegalArgumentException("调整数量不能为 0");
        boolean ok = delta.signum() > 0
                ? productMapper.addStock(id, delta) == 1
                : productMapper.deductStock(id, delta.negate()) == 1;
        if (!ok) throw new IllegalStateException("库存不足或商品不存在，调整失败");
        Product product = productMapper.selectById(id);
        StockLog logEntry = new StockLog();
        logEntry.setProductId(id);
        logEntry.setDelta(delta);
        logEntry.setStockAfter(product.getStock());
        logEntry.setReason(reason);
        logEntry.setOperatorAdminId(operatorAdminId);
        stockLogMapper.insert(logEntry);
        auditService.record(operatorAdminId, null, "STOCK_ADJUST",
                product.getSku(), "调整 " + delta + "，调整后库存 " + product.getStock() + "，原因：" + reason);
        invalidate(id);
        return logEntry;
    }

    public PageResult<StockLog> stockLogs(Long productId, long page, long size) {
        var query = new LambdaQueryWrapper<StockLog>()
                .eq(productId != null, StockLog::getProductId, productId)
                .orderByDesc(StockLog::getId);
        Page<StockLog> result = stockLogMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 低库存预警：在售且库存不高于阈值（系统设置 stock.low_warning_threshold，默认 50）。 */
    public List<Product> lowStock() {
        BigDecimal threshold;
        try {
            threshold = new BigDecimal(settingService.get("stock.low_warning_threshold", "50"));
        } catch (NumberFormatException ex) {
            threshold = new BigDecimal("50");
        }
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .le(Product::getStock, threshold)
                .orderByAsc(Product::getStock));
    }

    /**
     * Excel 批量导入商品。模板列（第 1 行表头，从第 2 行开始）：
     * SKU* | 名称* | 分类ID | 品牌 | 规格 | 单位 | 基准价* | 库存 | 起订量
     * SKU 已存在时更新价格/库存/名称等字段，否则新增商品（默认上架）。
     */
    @Transactional
    public ImportResult importExcel(InputStream input) {
        List<String> errors = new java.util.ArrayList<>();
        int created = 0;
        int updated = 0;
        try (Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String sku = cellText(row.getCell(0));
                String name = cellText(row.getCell(1));
                if ((sku == null || sku.isBlank()) && (name == null || name.isBlank())) continue;
                try {
                    if (sku == null || sku.isBlank()) throw new IllegalArgumentException("SKU 为空");
                    if (name == null || name.isBlank()) throw new IllegalArgumentException("名称为空");
                    Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>().eq(Product::getSku, sku.trim()));
                    boolean isNew = product == null;
                    if (isNew) {
                        product = new Product();
                        product.setSku(sku.trim());
                        product.setStatus(1);
                    }
                    product.setName(name.trim());
                    String categoryId = cellText(row.getCell(2));
                    if (categoryId != null && !categoryId.isBlank()) product.setCategoryId(Long.parseLong(categoryId.trim()));
                    product.setBrand(cellText(row.getCell(3)));
                    product.setSpecification(cellText(row.getCell(4)));
                    product.setUnit(cellText(row.getCell(5)));
                    String price = cellText(row.getCell(6));
                    if (price == null || price.isBlank()) throw new IllegalArgumentException("基准价为空");
                    product.setBasePrice(new BigDecimal(price.trim()));
                    String stock = cellText(row.getCell(7));
                    product.setStock(stock == null || stock.isBlank() ? BigDecimal.ZERO : new BigDecimal(stock.trim()));
                    String minQty = cellText(row.getCell(8));
                    if (minQty != null && !minQty.isBlank()) product.setMinOrderQty(new BigDecimal(minQty.trim()));
                    if (isNew) {
                        productMapper.insert(product);
                        created++;
                    } else {
                        productMapper.updateById(product);
                        updated++;
                    }
                    invalidate(product.getId());
                } catch (Exception ex) {
                    errors.add("第" + (i + 1) + "行：" + ex.getMessage());
                }
            }
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Excel 文件解析失败：" + ex.getMessage());
        }
        return new ImportResult(created, updated, errors);
    }

    private static String cellText(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getStringCellValue();
            default -> null;
        };
    }

    public record ImportResult(int created, int updated, List<String> errors) {
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
