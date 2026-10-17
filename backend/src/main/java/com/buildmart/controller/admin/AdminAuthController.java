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
    /** 连续失败 5 次锁定 10 分钟（Redis 计数；Redis 故障时退化为不锁定，仅记录日志）。 */
    private static final int MAX_FAILURES = 5;
    private static final java.time.Duration LOCK_TTL = java.time.Duration.ofMinutes(10);

    private final SysAdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final com.buildmart.service.RedisCacheService cacheService;
    private final com.buildmart.service.AdminAuditService auditService;

    public AdminAuthController(SysAdminMapper adminMapper, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
                               com.buildmart.service.RedisCacheService cacheService,
                               com.buildmart.service.AdminAuditService auditService) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.cacheService = cacheService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    @Operation(summary = "管理员登录", description = "使用管理员账号密码登录并获取 Bearer JWT。连续失败 5 次锁定 10 分钟。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数校验失败、账号密码错误或账号已锁定")
    })
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            jakarta.servlet.http.HttpServletRequest http) {
        String username = request.username().trim();
        String failKey = com.buildmart.common.CacheKeys.AUTH_TOKEN_PREFIX + "admin-login-fail:" + username;
        if (cacheService.hasKey(failKey + ":lock")) {
            throw new IllegalStateException("失败次数过多，账号已锁定，请 10 分钟后再试");
        }
        SysAdmin admin = adminMapper.selectOne(new LambdaQueryWrapper<SysAdmin>().eq(SysAdmin::getUsername, username));
        if (admin == null || admin.getStatus() == null || admin.getStatus() != 1
                || !passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            String current = cacheService.getRaw(failKey);
            long failures = (current == null ? 0 : Long.parseLong(current)) + 1;
            cacheService.putRaw(failKey, String.valueOf(failures), LOCK_TTL);
            if (failures >= MAX_FAILURES) {
                cacheService.putRaw(failKey + ":lock", "1", LOCK_TTL);
                cacheService.delete(failKey);
            }
            auditService.record(admin == null ? null : admin.getId(), username, "LOGIN_FAILED",
                    username, "密码错误或账号不可用，第 " + failures + " 次失败", clientIp(http));
            throw new IllegalArgumentException("用户名或密码错误");
        }
        cacheService.delete(failKey);
        cacheService.delete(failKey + ":lock");
        auditService.record(admin.getId(), username, "LOGIN", username, "登录成功", clientIp(http));
        return ApiResponse.ok(new LoginResponse(jwtTokenService.issue(admin.getId(), admin.getUsername(), admin.getRoleCode()), admin.getRealName(), admin.getRoleCode()));
    }

    private static String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
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
