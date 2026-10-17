package com.buildmart.controller.admin;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.PageResult;
import com.buildmart.entity.SysUser;
import com.buildmart.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "管理端客户", description = "小程序客户查询、状态与信用额度管理")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/customers")
public class CustomerAdminController {
    private final CustomerService customerService;

    public CustomerAdminController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @Operation(summary = "分页查询客户", description = "支持昵称/手机号/公司名关键字与状态筛选。")
    public ApiResponse<PageResult<SysUser>> page(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(customerService.page(page, size, keyword, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "客户详情", description = "客户资料 + 订单数、累计消费、最近下单时间。")
    public ApiResponse<CustomerService.CustomerDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(customerService.detail(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "启用/禁用客户", description = "禁用后该客户无法再登录小程序。")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam Integer value) {
        customerService.changeStatus(id, value);
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}/credit")
    @Operation(summary = "调整信用额度", description = "设置客户的赊销信用额度（元）。")
    public ApiResponse<Void> changeCreditLimit(@PathVariable Long id, @RequestParam BigDecimal value) {
        customerService.changeCreditLimit(id, value);
        return ApiResponse.ok();
    }
}
