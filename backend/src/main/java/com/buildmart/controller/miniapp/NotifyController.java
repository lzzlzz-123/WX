package com.buildmart.controller.miniapp;

import com.buildmart.common.ApiResponse;
import com.buildmart.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "小程序订阅消息", description = "下发已配置的订阅消息模板 ID，供 wx.requestSubscribeMessage 使用")
@RestController
@RequestMapping("/miniapp/notify")
public class NotifyController {
    private final SettingService settingService;

    public NotifyController(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping("/templates")
    @Operation(summary = "订阅消息模板 ID 列表", description = "未配置的模板返回空字符串，小程序应跳过对应的订阅请求。")
    public ApiResponse<Map<String, String>> templates() {
        Map<String, String> templates = new LinkedHashMap<>();
        templates.put("quoteReplied", settingService.get("wechat.template.quote_replied", ""));
        templates.put("orderShipped", settingService.get("wechat.template.order_shipped", ""));
        return ApiResponse.ok(templates);
    }
}
