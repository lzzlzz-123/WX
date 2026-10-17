package com.buildmart.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.buildmart.common.ApiResponse;
import com.buildmart.entity.SysAdmin;
import com.buildmart.mapper.SysAdminMapper;
import com.buildmart.service.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端认证", description = "管理员登录与令牌签发")
@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {
    private final SysAdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AdminAuthController(SysAdminMapper adminMapper, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    @Operation(summary = "管理员登录", description = "使用管理员账号密码登录并获取 Bearer JWT。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数校验失败或账号密码错误")
    })
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        SysAdmin admin = adminMapper.selectOne(new LambdaQueryWrapper<SysAdmin>().eq(SysAdmin::getUsername, request.username()));
        if (admin == null || admin.getStatus() == null || admin.getStatus() != 1
                || !passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return ApiResponse.ok(new LoginResponse(jwtTokenService.issue(admin.getId(), admin.getUsername(), admin.getRoleCode()), admin.getRealName(), admin.getRoleCode()));
    }

    public record LoginRequest(
            @Schema(description = "管理员账号", example = "admin") @NotBlank String username,
            @Schema(description = "管理员密码", example = "password", format = "password") @NotBlank String password) {
    }

    public record LoginResponse(
            @Schema(description = "JWT 令牌") String token,
            @Schema(description = "管理员姓名") String realName,
            @Schema(description = "角色编码", example = "SUPER_ADMIN") String roleCode) {
    }
}
