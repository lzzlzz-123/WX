package com.buildmart.controller.admin;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.PageResult;
import com.buildmart.entity.AdminOperationLog;
import com.buildmart.service.AdminAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端审计日志", description = "管理员关键操作记录查询")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/audit-logs")
public class AdminAuditController {
    private final AdminAuditService auditService;

    public AdminAuditController(AdminAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "分页查询操作日志", description = "按操作类型筛选，倒序返回。")
    public ApiResponse<PageResult<AdminOperationLog>> page(@RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "20") long size,
                                                           @RequestParam(required = false) String action) {
        return ApiResponse.ok(auditService.page(page, size, action));
    }
}
