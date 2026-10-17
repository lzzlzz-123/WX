package com.buildmart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@Tag(name = "服务检查", description = "服务存活与运行状态")
@RestController
@RequestMapping("/health")
public class HealthController {
    @Operation(summary = "健康检查", description = "检查后端是否可以接收请求。无需登录。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "服务正常"),
            @ApiResponse(responseCode = "500", description = "服务异常")
    })
    @GetMapping
    public com.buildmart.common.ApiResponse<Map<String, Object>> health() {
        return com.buildmart.common.ApiResponse.ok(Map.of("status", "UP", "service", "buildmart-backend", "time", OffsetDateTime.now().toString()));
    }
}
