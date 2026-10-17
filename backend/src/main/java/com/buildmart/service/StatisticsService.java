package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.buildmart.entity.Inquiry;
import com.buildmart.entity.Product;
import com.buildmart.entity.PurchaseOrder;
import com.buildmart.entity.SysUser;
import com.buildmart.mapper.InquiryMapper;
import com.buildmart.mapper.ProductMapper;
import com.buildmart.mapper.PurchaseOrderMapper;
import com.buildmart.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin-side data statistics: overview cards and time-series trends.
 * All figures are computed from MySQL directly (facts, not cache) so the
 * dashboard never shows stale totals.
 */
@Service
public class StatisticsService {
    private final PurchaseOrderMapper orderMapper;
    private final SysUserMapper userMapper;
    private final ProductMapper productMapper;
    private final InquiryMapper inquiryMapper;

    public StatisticsService(PurchaseOrderMapper orderMapper, SysUserMapper userMapper,
                             ProductMapper productMapper, InquiryMapper inquiryMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.productMapper = productMapper;
        this.inquiryMapper = inquiryMapper;
    }

    /** Overview cards: cumulative + today figures for orders, sales, customers, inquiries, products. */
    public StatisticsOverview overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long totalOrderCount = orderMapper.selectCount(new QueryWrapper<>());
        long todayOrderCount = orderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .ge(PurchaseOrder::getCreatedAt, todayStart));
        BigDecimal totalSalesAmount = sumPayable(null);
        BigDecimal todaySalesAmount = sumPayable(todayStart);
        long pendingShipmentCount = orderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getStatus, "PENDING_SHIPMENT"));
        long pendingPaymentCount = orderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getStatus, "PENDING_PAYMENT"));

        long totalCustomerCount = userMapper.selectCount(new QueryWrapper<>());
        long todayNewCustomerCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .ge(SysUser::getCreatedAt, todayStart));

        long activeProductCount = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1));
        long lowStockCount = productMapper.selectCount(new QueryWrapper<Product>()
                .eq("status", 1)
                .apply("stock <= min_order_qty"));

        long pendingInquiryCount = inquiryMapper.selectCount(new LambdaQueryWrapper<Inquiry>()
                .eq(Inquiry::getStatus, "PENDING_QUOTE"));

        return new StatisticsOverview(
                totalOrderCount, todayOrderCount, totalSalesAmount, todaySalesAmount,
                pendingShipmentCount, pendingPaymentCount,
                totalCustomerCount, todayNewCustomerCount,
                activeProductCount, lowStockCount, pendingInquiryCount);
    }

    /**
     * Order/sales trend grouped by day, ISO week or month over the last N
     * periods. Buckets with no data are filled with zeros so charts render
     * continuous series.
     *
     * @param dimension day | week | month
     * @param periods   number of buckets to return (1-90)
     */
    public TrendResult trend(String dimension, int periods) {
        String dim = dimension == null || dimension.isBlank() ? "day" : dimension.trim().toLowerCase(Locale.ROOT);
        if (!dim.equals("day") && !dim.equals("week") && !dim.equals("month")) {
            throw new IllegalArgumentException("dimension 仅支持 day / week / month");
        }
        int count = Math.min(Math.max(periods, 1), 90);

        List<String> buckets = buildBuckets(dim, count);
        LocalDateTime since = bucketStart(dim, count);

        Map<String, Long> orderCounts = new LinkedHashMap<>();
        Map<String, BigDecimal> salesAmounts = new LinkedHashMap<>();
        Map<String, Long> customerCounts = new LinkedHashMap<>();
        buckets.forEach(b -> {
            orderCounts.put(b, 0L);
            salesAmounts.put(b, BigDecimal.ZERO);
            customerCounts.put(b, 0L);
        });

        String groupExpr = switch (dim) {
            case "week" -> "DATE_FORMAT(created_at, '%x-%v')";
            case "month" -> "DATE_FORMAT(created_at, '%Y-%m')";
            default -> "DATE(created_at)";
        };

        orderMapper.selectMaps(new QueryWrapper<PurchaseOrder>()
                        .select(groupExpr + " AS bucket", "COUNT(*) AS cnt", "COALESCE(SUM(payable_amount), 0) AS amount")
                        .ge("created_at", since)
                        .groupBy("bucket"))
                .forEach(row -> {
                    String bucket = String.valueOf(row.get("bucket"));
                    if (orderCounts.containsKey(bucket)) {
                        orderCounts.put(bucket, ((Number) row.get("cnt")).longValue());
                        salesAmounts.put(bucket, toBigDecimal(row.get("amount")));
                    }
                });

        userMapper.selectMaps(new QueryWrapper<SysUser>()
                        .select(groupExpr + " AS bucket", "COUNT(*) AS cnt")
                        .ge("created_at", since)
                        .groupBy("bucket"))
                .forEach(row -> {
                    String bucket = String.valueOf(row.get("bucket"));
                    if (customerCounts.containsKey(bucket)) {
                        customerCounts.put(bucket, ((Number) row.get("cnt")).longValue());
                    }
                });

        List<TrendPoint> points = new ArrayList<>(buckets.size());
        for (String bucket : buckets) {
            points.add(new TrendPoint(bucket, orderCounts.get(bucket), salesAmounts.get(bucket), customerCounts.get(bucket)));
        }
        return new TrendResult(dim, points);
    }

    private List<String> buildBuckets(String dim, int count) {
        List<String> buckets = new ArrayList<>(count);
        LocalDate today = LocalDate.now();
        DateTimeFormatter weekFmt = DateTimeFormatter.ofPattern("YYYY-ww", Locale.ROOT);
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);
        for (int i = count - 1; i >= 0; i--) {
            buckets.add(switch (dim) {
                case "week" -> today.minusWeeks(i).format(weekFmt);
                case "month" -> today.minusMonths(i).format(monthFmt);
                default -> today.minusDays(i).toString();
            });
        }
        return buckets;
    }

    private LocalDateTime bucketStart(String dim, int count) {
        LocalDate today = LocalDate.now();
        return switch (dim) {
            // ISO week starts Monday; align the earliest bucket to its Monday.
            case "week" -> today.minusWeeks(count - 1)
                    .with(WeekFields.ISO.dayOfWeek(), 1).atStartOfDay();
            case "month" -> today.minusMonths(count - 1).withDayOfMonth(1).atStartOfDay();
            default -> today.minusDays(count - 1).atStartOfDay();
        };
    }

    private BigDecimal sumPayable(LocalDateTime since) {
        QueryWrapper<PurchaseOrder> query = new QueryWrapper<PurchaseOrder>()
                .select("COALESCE(SUM(payable_amount), 0) AS payable_amount")
                // Cancelled orders must not inflate sales figures.
                .ne("status", "CANCELLED");
        if (since != null) query.ge("created_at", since);
        Object value = orderMapper.selectObjs(query).stream().findFirst().orElse(null);
        return value == null ? BigDecimal.ZERO : toBigDecimal(value);
    }

    private BigDecimal toBigDecimal(Object value) {
        try {
            return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    public record StatisticsOverview(
            long totalOrderCount,
            long todayOrderCount,
            BigDecimal totalSalesAmount,
            BigDecimal todaySalesAmount,
            long pendingShipmentCount,
            long pendingPaymentCount,
            long totalCustomerCount,
            long todayNewCustomerCount,
            long activeProductCount,
            long lowStockCount,
            long pendingInquiryCount) {
    }

    public record TrendPoint(String bucket, long orderCount, BigDecimal salesAmount, long newCustomerCount) {
    }

    public record TrendResult(String dimension, List<TrendPoint> points) {
    }
}
