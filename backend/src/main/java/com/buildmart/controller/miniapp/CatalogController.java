package com.buildmart.controller.miniapp;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.PageResult;
import com.buildmart.entity.Product;
import com.buildmart.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小程序商品目录", description = "商品浏览与详情")
@RestController
@RequestMapping("/miniapp/catalog")
public class CatalogController {
    private final ProductService productService;

    public CatalogController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    @Operation(summary = "分页查询在售商品", description = "keyword 模糊匹配名称/SKU/规格，支持品牌与分类筛选。")
    public ApiResponse<PageResult<Product>> products(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long size,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String brand,
                                                      @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(productService.page(page, size, keyword, brand, categoryId, 1));
    }

    @GetMapping("/brands")
    @Operation(summary = "在售商品品牌列表", description = "用于小程序筛选面板。")
    public ApiResponse<java.util.List<String>> brands() {
        return ApiResponse.ok(productService.brands());
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "查询商品详情")
    public ApiResponse<Product> product(@PathVariable Long id) {
        return ApiResponse.ok(productService.get(id));
    }
}
