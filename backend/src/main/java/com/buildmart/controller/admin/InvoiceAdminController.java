package com.buildmart.controller.admin;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.PageResult;
import com.buildmart.entity.InvoiceRequest;
import com.buildmart.service.InvoiceRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端开票", description = "开票申请处理")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/invoice-requests")
public class InvoiceAdminController {
    private final InvoiceRequestService requestService;

    public InvoiceAdminController(InvoiceRequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping
    @Operation(summary = "分页查询开票申请")
    public ApiResponse<PageResult<InvoiceRequest>> page(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size,
                                                        @RequestParam(required = false) String status) {
        return ApiResponse.ok(requestService.page(page, size, status));
    }

    @PostMapping("/{id}/issue")
    @Operation(summary = "开具发票")
    public ApiResponse<Void> issue(@PathVariable Long id) {
        requestService.issue(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "驳回开票申请")
    public ApiResponse<Void> reject(@PathVariable Long id, @RequestBody RejectRequest request) {
        requestService.reject(id, request.reason());
        return ApiResponse.ok();
    }

    public record RejectRequest(@Schema(description = "驳回原因") String reason) {
    }
}
