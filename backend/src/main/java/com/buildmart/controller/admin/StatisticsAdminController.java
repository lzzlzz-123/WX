package com.buildmart.controller.admin;

import com.buildmart.common.ApiResponse;
import com.buildmart.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端数据统计", description = "经营数据总览与趋势分析")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/statistics")
public class StatisticsAdminController {
    private final StatisticsService statisticsService;

    public StatisticsAdminController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    @Operation(summary = "经营数据总览", description = "累计/今日订单、销售额、客户数、待办（待发货、待报价）与商品健康度指标。")
    public ApiResponse<StatisticsService.StatisticsOverview> overview() {
        return ApiResponse.ok(statisticsService.overview());
    }

    @GetMapping("/trend")
    @Operation(summary = "经营趋势", description = "按天/周/月分桶输出订单量、销售额、新增客户趋势，空桶补零。")
    public ApiResponse<StatisticsService.TrendResult> trend(
            @RequestParam(defaultValue = "day") String dimension,
            @RequestParam(defaultValue = "30") int periods) {
        return ApiResponse.ok(statisticsService.trend(dimension, periods));
    }
}
