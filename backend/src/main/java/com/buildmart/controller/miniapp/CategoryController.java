package com.buildmart.controller.miniapp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.buildmart.common.ApiResponse;
import com.buildmart.entity.ProductCategory;
import com.buildmart.mapper.ProductCategoryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "小程序分类", description = "商品分类查询")
@RestController
@RequestMapping("/miniapp/catalog")
public class CategoryController {
    private final ProductCategoryMapper categoryMapper;

    public CategoryController(ProductCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @GetMapping("/categories")
    @Operation(summary = "查询商品分类")
    public ApiResponse<List<ProductCategory>> categories() {
        return ApiResponse.ok(categoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getStatus, 1).orderByAsc(ProductCategory::getSortNo)));
    }
}
