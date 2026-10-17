package com.buildmart.controller.admin;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.PageResult;
import com.buildmart.entity.OrderShipment;
import com.buildmart.entity.PurchaseOrder;
import com.buildmart.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "管理端订单", description = "订单查询、发货与退款操作")
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
    @Operation(summary = "订单发货", description = "将待发货订单推进为已发货状态。配送订单需填写物流公司与运单号；自提订单自动生成核销码。")
    public ApiResponse<OrderShipment> ship(@PathVariable Long id, @RequestBody(required = false) ShipRequest request) {
        ShipRequest body = request == null ? new ShipRequest(null, null, null) : request;
        return ApiResponse.ok(orderService.ship(id, body.logisticsCompany(), body.trackingNo(), body.remark()));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "订单退款", description = "对待发货订单发起微信原路退款，退款结果由异步通知入账。")
    public ApiResponse<Void> refund(@PathVariable Long id, @RequestBody(required = false) RefundRequest request) {
        RefundRequest body = request == null ? new RefundRequest(null, null) : request;
        orderService.refund(id, body.amount(), body.reason());
        return ApiResponse.ok();
    }

    public record ShipRequest(@Schema(description = "物流公司（配送订单必填）") String logisticsCompany,
                              @Schema(description = "运单号（配送订单必填）") String trackingNo,
                              @Schema(description = "发货备注") String remark) {
    }

    public record RefundRequest(@Schema(description = "退款金额，缺省为全额退款") BigDecimal amount,
                                @Schema(description = "退款原因") String reason) {
    }
}
