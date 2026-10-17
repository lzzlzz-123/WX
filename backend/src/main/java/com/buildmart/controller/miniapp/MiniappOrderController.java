package com.buildmart.controller.miniapp;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.AuthContext;
import com.buildmart.common.PageResult;
import com.buildmart.entity.PurchaseOrder;
import com.buildmart.service.MiniappOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "小程序订单", description = "采购订单创建与查询")
@RestController
@RequestMapping("/miniapp/orders")
public class MiniappOrderController {
    private final MiniappOrderService orderService;
    private final com.buildmart.service.WxPayService wxPayService;

    public MiniappOrderController(MiniappOrderService orderService, com.buildmart.service.WxPayService wxPayService) {
        this.orderService = orderService;
        this.wxPayService = wxPayService;
    }

    @PostMapping
    @Operation(summary = "创建采购订单", description = "根据商品明细创建订单，订单状态由服务层控制。")
    public ApiResponse<PurchaseOrder> create(@RequestBody CreateOrderRequest request) {
        List<MiniappOrderService.OrderLine> lines = request.items().stream()
                .map(item -> new MiniappOrderService.OrderLine(item.productId(), item.quantity())).toList();
        return ApiResponse.ok(orderService.create(AuthContext.requireUserId(), lines, request.receiverName(), request.receiverPhone(),
                request.receiverAddress(), request.deliveryMode(), request.remark()));
    }

    @GetMapping
    @Operation(summary = "分页查询我的订单")
    public ApiResponse<PageResult<PurchaseOrder>> page(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size,
                                                        @RequestParam(required = false) String status) {
        return ApiResponse.ok(orderService.page(AuthContext.requireUserId(), page, size, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询订单详情")
    public ApiResponse<MiniappOrderService.OrderDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.detail(AuthContext.requireUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消订单", description = "仅待支付订单可取消，取消后回补库存。")
    public ApiResponse<PurchaseOrder> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(AuthContext.requireUserId(), id));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "确认收货", description = "仅待收货订单可确认，确认后订单完成。")
    public ApiResponse<PurchaseOrder> confirm(@PathVariable Long id) {
        return ApiResponse.ok(orderService.confirmReceipt(AuthContext.requireUserId(), id));
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "发起微信支付", description = "对待支付订单进行 JSAPI 下单，返回 wx.requestPayment 所需参数。")
    public ApiResponse<java.util.Map<String, String>> pay(@PathVariable Long id) {
        return ApiResponse.ok(wxPayService.createJsapiPayment(AuthContext.requireUserId(), id));
    }

    public record CreateOrderRequest(@Schema(description = "订单商品明细") List<OrderItemRequest> items,
                                     @Schema(description = "收货人姓名") String receiverName,
                                     @Schema(description = "收货人电话") String receiverPhone,
                                     @Schema(description = "收货地址") String receiverAddress,
                                     @Schema(description = "配送方式") String deliveryMode,
                                     @Schema(description = "订单备注") String remark) {
    }

    public record OrderItemRequest(@Schema(description = "商品 ID", example = "1") Long productId,
                                   @Schema(description = "商品数量", example = "10") BigDecimal quantity) {
    }
}
