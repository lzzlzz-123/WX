package com.buildmart.controller.miniapp;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.AuthContext;
import com.buildmart.entity.CartItem;
import com.buildmart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "小程序购物车", description = "购物车增删改查")
@RestController
@RequestMapping("/miniapp/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "查询购物车")
    public ApiResponse<List<CartItem>> list() {
        return ApiResponse.ok(cartService.list(AuthContext.requireUserId()));
    }

    @PostMapping
    @Operation(summary = "添加商品到购物车")
    public ApiResponse<CartItem> add(@RequestBody CartRequest request) {
        return ApiResponse.ok(cartService.add(AuthContext.requireUserId(), request.productId(), request.quantity()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新购物车商品")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody CartUpdateRequest request) {
        cartService.update(AuthContext.requireUserId(), id, request.quantity(), request.selected());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除购物车商品")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        cartService.delete(AuthContext.requireUserId(), id);
        return ApiResponse.ok();
    }

    public record CartRequest(@Schema(description = "商品 ID", example = "1") Long productId,
                              @Schema(description = "购买数量", example = "10") BigDecimal quantity) {
    }

    public record CartUpdateRequest(@Schema(description = "购买数量", example = "10") BigDecimal quantity,
                                    @Schema(description = "是否选中，1 是 0 否", example = "1") Integer selected) {
    }
}
