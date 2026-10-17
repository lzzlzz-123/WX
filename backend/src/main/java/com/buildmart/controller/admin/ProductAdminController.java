package com.buildmart.controller.admin;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.PageResult;
import com.buildmart.entity.Product;
import com.buildmart.entity.ProductTierPrice;
import com.buildmart.service.ProductService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

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

    public record ProductSaveRequest(
            @Schema(description = "商品信息") Product product,
            @Schema(description = "阶梯价格列表") List<ProductTierPrice> tiers) {
    }
}
