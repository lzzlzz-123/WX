package com.buildmart.controller.admin;

import com.buildmart.common.ApiResponse;
import com.buildmart.entity.SysAdmin;
import com.buildmart.service.AdminAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理端账号权限", description = "管理员账号与角色权限管理，仅超级管理员可操作")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/accounts")
public class AdminAccountController {
    private final AdminAccountService accountService;

    public AdminAccountController(AdminAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "管理员列表")
    public ApiResponse<List<SysAdmin>> list() {
        return ApiResponse.ok(accountService.list());
    }

    @PostMapping
    @Operation(summary = "新增管理员")
    public ApiResponse<SysAdmin> create(@RequestBody CreateAdminRequest request) {
        return ApiResponse.ok(accountService.create(request.username(), request.password(), request.realName(), request.roleCode()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑管理员", description = "修改姓名与角色。")
    public ApiResponse<SysAdmin> update(@PathVariable Long id, @RequestBody UpdateAdminRequest request) {
        return ApiResponse.ok(accountService.update(id, request.realName(), request.roleCode()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "启用/停用管理员")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestParam Integer value) {
        accountService.changeStatus(id, value);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置密码")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest request) {
        accountService.resetPassword(id, request.password());
        return ApiResponse.ok();
    }

    @GetMapping("/roles")
    @Operation(summary = "角色权限列表")
    public ApiResponse<Map<String, List<String>>> rolePermissions() {
        return ApiResponse.ok(accountService.rolePermissions());
    }

    @PutMapping("/roles/{roleCode}")
    @Operation(summary = "保存角色权限", description = "覆盖式更新角色权限码；SUPER_ADMIN 不可修改。")
    public ApiResponse<Void> saveRolePermissions(@PathVariable String roleCode, @RequestBody List<String> permissions) {
        accountService.saveRolePermissions(roleCode, permissions);
        return ApiResponse.ok();
    }

    public record CreateAdminRequest(@Schema(description = "用户名") String username,
                                     @Schema(description = "初始密码，至少 8 位") String password,
                                     @Schema(description = "姓名") String realName,
                                     @Schema(description = "角色：SUPER_ADMIN/OPERATOR/FINANCE") String roleCode) {
    }

    public record UpdateAdminRequest(@Schema(description = "姓名") String realName,
                                     @Schema(description = "角色") String roleCode) {
    }

    public record ResetPasswordRequest(@Schema(description = "新密码，至少 8 位") String password) {
    }
}
