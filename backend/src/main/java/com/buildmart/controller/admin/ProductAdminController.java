package com.buildmart.controller.admin;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.AuthContext;
import com.buildmart.common.PageResult;
import com.buildmart.entity.Product;
import com.buildmart.entity.ProductTierPrice;
import com.buildmart.entity.StockLog;
import com.buildmart.service.ProductService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "管理端商品", description = "商品和阶梯价格维护")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/products")
public class ProductAdminController {
    private final ProductService productService;

    public ProductAdminController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "分页查询商品", description = "按商品名称或 SKU 搜索，并支持按状态筛选。")
    public ApiResponse<PageResult<Product>> page(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "20") long size,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(productService.page(page, size, keyword, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询商品详情")
    public ApiResponse<Product> detail(@PathVariable Long id) {
        return ApiResponse.ok(productService.get(id));
    }

    @PostMapping
    @Operation(summary = "新增或更新商品", description = "请求体包含商品信息和阶梯价格；有 id 时更新，无 id 时新增。")
    public ApiResponse<Product> save(@Valid @RequestBody ProductSaveRequest request) {
        return ApiResponse.ok(productService.save(request.product(), request.tiers()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "修改商品状态", description = "修改商品上下架状态，通常使用 1 上架、0 下架。")
    public ApiResponse<Void> status(@PathVariable Long id, @RequestParam Integer value) {
        productService.changeStatus(id, value);
        return ApiResponse.ok();
    }

    @PatchMapping("/batch-status")
    @Operation(summary = "批量上下架", description = "对一批商品统一设置上架（1）或下架（0）。")
    public ApiResponse<Integer> batchStatus(@RequestBody BatchStatusRequest request) {
        return ApiResponse.ok(productService.batchChangeStatus(request.ids(), request.value()));
    }

    @PostMapping("/{id}/stock-adjust")
    @Operation(summary = "库存盘点调整", description = "正数入库、负数出库（不足则失败），记录操作流水。")
    public ApiResponse<StockLog> stockAdjust(@PathVariable Long id, @RequestBody StockAdjustRequest request) {
        return ApiResponse.ok(productService.adjustStock(id, request.delta(), request.reason(), AuthContext.requireAdminId()));
    }

    @GetMapping("/stock-logs")
    @Operation(summary = "库存调整流水")
    public ApiResponse<PageResult<StockLog>> stockLogs(@RequestParam(required = false) Long productId,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(productService.stockLogs(productId, page, size));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "低库存预警列表", description = "在售且库存不高于阈值（设置项 stock.low_warning_threshold，默认 50）的商品。")
    public ApiResponse<List<Product>> lowStock() {
        return ApiResponse.ok(productService.lowStock());
    }

    @PostMapping("/import")
    @Operation(summary = "Excel 批量导入商品", description = "模板列：SKU* | 名称* | 分类ID | 品牌 | 规格 | 单位 | 基准价* | 库存 | 起订量。SKU 已存在则更新。")
    public ApiResponse<ProductService.ImportResult> importExcel(@RequestParam("file") MultipartFile file) throws java.io.IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请上传 Excel 文件");
        return ApiResponse.ok(productService.importExcel(file.getInputStream()));
    }

    public record BatchStatusRequest(@Schema(description = "商品 ID 列表") List<Long> ids,
                                     @Schema(description = "目标状态 1 上架 / 0 下架") Integer value) {
    }

    public record StockAdjustRequest(@Schema(description = "调整量，正入负出") java.math.BigDecimal delta,
                                     @Schema(description = "调整原因") String reason) {
    }

    public record ProductSaveRequest(
            @Schema(description = "商品信息") Product product,
            @Schema(description = "阶梯价格列表") List<ProductTierPrice> tiers) {
    }
}
