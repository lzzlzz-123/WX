package com.buildmart.controller.admin;

import com.buildmart.common.ApiResponse;
import com.buildmart.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理端系统设置", description = "键值型系统配置的读取与保存")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/settings")
public class SettingAdminController {
    private final SettingService settingService;

    public SettingAdminController(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    @Operation(summary = "读取全部设置", description = "返回 key -> value 的系统设置全集。")
    public ApiResponse<Map<String, String>> all() {
        return ApiResponse.ok(settingService.all());
    }

    @PutMapping
    @Operation(summary = "保存设置", description = "按键名 upsert；请求体为 key -> value 的 JSON 对象。")
    public ApiResponse<Void> save(@RequestBody Map<String, String> settings) {
        settingService.save(settings);
        return ApiResponse.ok();
    }
}
