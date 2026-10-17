package com.buildmart.controller.admin;

import com.buildmart.common.ApiResponse;
import com.buildmart.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端概览", description = "管理首页实时统计")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/dashboard")
public class DashboardAdminController {
    private final DashboardService dashboardService;

    public DashboardAdminController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "查询首页实时统计", description = "从商品、订单和询价表汇总当前管理端概览数据。")
    public ApiResponse<DashboardService.DashboardSummary> summary() {
        return ApiResponse.ok(dashboardService.summary());
    }
}
