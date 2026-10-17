package com.buildmart.controller.admin;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.AuthContext;
import com.buildmart.common.PageResult;
import com.buildmart.entity.Inquiry;
import com.buildmart.entity.InquiryQuote;
import com.buildmart.service.InquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Tag(name = "管理端询价", description = "询价单查询")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/inquiries")
public class InquiryAdminController {
    private final InquiryService inquiryService;

    public InquiryAdminController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @GetMapping
    @Operation(summary = "分页查询询价单", description = "管理端按询价状态分页查询用户询价。")
    public ApiResponse<PageResult<Inquiry>> page(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(required = false) String status) {
        return ApiResponse.ok(inquiryService.page(page, size, status));
    }

    @PostMapping("/{id}/quote")
    @Operation(summary = "对询价单报价", description = "写入报价单并对用户可见，询价单状态推进为 QUOTED。")
    public ApiResponse<InquiryQuote> quote(@PathVariable Long id, @RequestBody QuoteRequest request) {
        return ApiResponse.ok(inquiryService.quote(id, AuthContext.getAdminId(), request.quotePrice(),
                request.validUntil(), request.deliveryFee(), request.remark()));
    }

    public record QuoteRequest(@Schema(description = "报价单价（元）", example = "312.00") BigDecimal quotePrice,
                               @Schema(description = "报价有效期至", example = "2026-10-20") LocalDate validUntil,
                               @Schema(description = "配送费（元）", example = "0") BigDecimal deliveryFee,
                               @Schema(description = "报价备注") String remark) {
    }
}
