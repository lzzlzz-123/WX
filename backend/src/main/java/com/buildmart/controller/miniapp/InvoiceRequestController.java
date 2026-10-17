package com.buildmart.controller.miniapp;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.AuthContext;
import com.buildmart.common.PageResult;
import com.buildmart.entity.InvoiceRequest;
import com.buildmart.service.InvoiceRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小程序开票申请", description = "订单开票申请与查询")
@RestController
@RequestMapping("/miniapp/invoice-requests")
public class InvoiceRequestController {
    private final InvoiceRequestService requestService;

    public InvoiceRequestController(InvoiceRequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    @Operation(summary = "申请开票", description = "对已支付订单申请开票，每单限一张。")
    public ApiResponse<InvoiceRequest> apply(@RequestBody ApplyRequest request) {
        return ApiResponse.ok(requestService.apply(AuthContext.requireUserId(), request.orderId(), request.titleId(), request.remark()));
    }

    @GetMapping
    @Operation(summary = "我的开票申请列表")
    public ApiResponse<PageResult<InvoiceRequest>> page(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(requestService.pageForUser(AuthContext.requireUserId(), page, size));
    }

    public record ApplyRequest(@Schema(description = "订单 ID") Long orderId,
                               @Schema(description = "发票抬头 ID") Long titleId,
                               @Schema(description = "备注") String remark) {
    }
}
