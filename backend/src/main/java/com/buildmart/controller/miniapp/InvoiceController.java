package com.buildmart.controller.miniapp;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.AuthContext;
import com.buildmart.entity.InvoiceTitle;
import com.buildmart.service.InvoiceTitleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "小程序发票", description = "发票抬头管理")
@RestController
@RequestMapping("/miniapp/invoices")
public class InvoiceController {
    private final InvoiceTitleService invoiceTitleService;

    public InvoiceController(InvoiceTitleService invoiceTitleService) {
        this.invoiceTitleService = invoiceTitleService;
    }

    @GetMapping
    @Operation(summary = "查询发票抬头列表")
    public ApiResponse<List<InvoiceTitle>> list() {
        return ApiResponse.ok(invoiceTitleService.list(AuthContext.requireUserId()));
    }

    @PostMapping
    @Operation(summary = "保存发票抬头", description = "新增或更新当前用户的发票抬头，企业抬头必须填税号。")
    public ApiResponse<InvoiceTitle> save(@RequestBody InvoiceTitle invoice) {
        return ApiResponse.ok(invoiceTitleService.save(AuthContext.requireUserId(), invoice));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除发票抬头")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        invoiceTitleService.delete(AuthContext.requireUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/default")
    @Operation(summary = "设为默认抬头")
    public ApiResponse<Void> setDefault(@PathVariable Long id) {
        invoiceTitleService.setDefault(AuthContext.requireUserId(), id);
        return ApiResponse.ok();
    }
}
