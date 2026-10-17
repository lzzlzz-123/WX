package com.buildmart.controller.admin;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.PageResult;
import com.buildmart.entity.PurchaseOrder;
import com.buildmart.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端订单", description = "订单查询与发货操作")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/orders")
public class OrderAdminController {
    private final OrderService orderService;

    public OrderAdminController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "分页查询订单", description = "管理端按订单状态分页查询采购订单。")
    public ApiResponse<PageResult<PurchaseOrder>> page(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size,
                                                        @RequestParam(required = false) String status) {
        return ApiResponse.ok(orderService.page(page, size, status));
    }

    @PostMapping("/{id}/ship")
    @Operation(summary = "订单发货", description = "将待发货订单推进为已发货状态。")
    public ApiResponse<Void> ship(@PathVariable Long id) {
        orderService.ship(id);
        return ApiResponse.ok();
    }
}
