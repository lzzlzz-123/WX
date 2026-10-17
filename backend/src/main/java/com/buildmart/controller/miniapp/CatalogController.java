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
    @Operation(summary = "分页查询在售商品", description = "仅返回状态为在售的商品。")
    public ApiResponse<PageResult<Product>> products(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long size,
                                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(productService.page(page, size, keyword, 1));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "查询商品详情")
    public ApiResponse<Product> product(@PathVariable Long id) {
        return ApiResponse.ok(productService.get(id));
    }
}
