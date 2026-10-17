package com.buildmart.controller.miniapp;

import com.buildmart.common.ApiResponse;
import com.buildmart.common.AuthContext;
import com.buildmart.common.AuthException;
import com.buildmart.entity.SysUser;
import com.buildmart.service.AvatarStorageService;
import com.buildmart.service.SmsAuthService;
import com.buildmart.service.UserService;
import com.buildmart.service.WechatAuthService;
import com.buildmart.service.JwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "小程序认证", description = "微信用户登录")
@RestController
@RequestMapping("/miniapp/auth")
public class MiniappAuthController {
    private final UserService userService;
    private final WechatAuthService wechatAuthService;
    private final JwtTokenService jwtTokenService;
    private final AvatarStorageService avatarStorageService;
    private final SmsAuthService smsAuthService;
    private final boolean debugOpenidEnabled;

    /** Production constructor. The openid bypass is opt-in and disabled by default. */
    @Autowired
    public MiniappAuthController(UserService userService, WechatAuthService wechatAuthService,
                                 JwtTokenService jwtTokenService,
                                 AvatarStorageService avatarStorageService,
                                 SmsAuthService smsAuthService,
                                 @Value("${wechat.debug-openid-enabled:false}") boolean debugOpenidEnabled) {
        this.userService = userService;
        this.wechatAuthService = wechatAuthService;
        this.jwtTokenService = jwtTokenService;
        this.avatarStorageService = avatarStorageService;
        this.smsAuthService = smsAuthService;
        this.debugOpenidEnabled = debugOpenidEnabled;
    }

    /** Kept for direct construction in existing tests and tools. */
    public MiniappAuthController(UserService userService, WechatAuthService wechatAuthService,
                                 JwtTokenService jwtTokenService) {
        this(userService, wechatAuthService, jwtTokenService, null,
                new SmsAuthService(null, 300, 60, false), false);
    }

    @PostMapping("/login")
    @Operation(summary = "微信用户登录", description = "接收微信 wx.login 返回的 code，通过 code2Session 换取 openid，创建或更新采购用户。")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        if (request == null) {
            throw new AuthException(AuthException.Reason.CODE_REQUIRED);
        }
        String openid;
        String unionid = null;
        if (request.openid() != null && !request.openid().isBlank()) {
            if (!debugOpenidEnabled) {
                throw new AuthException(AuthException.Reason.OPENID_NOT_ALLOWED);
            }
            // This path exists only for local server-side diagnostics. It is
            // deliberately impossible to use unless explicitly enabled.
            openid = request.openid().trim();
        } else {
            if (request.code() == null || request.code().isBlank()) {
                throw new AuthException(AuthException.Reason.CODE_REQUIRED);
            }
            WechatAuthService.WechatIdentity identity = wechatAuthService.resolveIdentity(request.code());
            openid = identity.openid();
            unionid = identity.unionid();
        }
        SysUser user = userService.login(openid, unionid, request.nickname(), request.phone());
        String token = jwtTokenService.issueUser(user.getId(), user.getOpenid());
        return ApiResponse.ok(new LoginResponse(user.getId(), user.getNickname(), user.getPhone(),
                user.getAvatarUrl(), token));
    }

    @PostMapping("/login-phone")
    @Operation(summary = "手机号一键登录注册", description = "同时接收 wx.login 的 code 和 getPhoneNumber 的 phoneCode，换取 openid 与手机号后创建或更新用户。新用户即注册，老用户即登录。")
    public ApiResponse<LoginResponse> loginByPhone(@RequestBody PhoneLoginRequest request) {
        if (request == null || request.loginCode() == null || request.loginCode().isBlank()) {
            throw new AuthException(AuthException.Reason.CODE_REQUIRED);
        }
        if (request.phoneCode() == null || request.phoneCode().isBlank()) {
            throw new AuthException(AuthException.Reason.PHONE_CODE_REQUIRED);
        }
        // Both codes are single-use; exchange the login code first so a
        // phone-code failure does not burn the wx.login session.
        WechatAuthService.WechatIdentity identity = wechatAuthService.resolveIdentity(request.loginCode());
        String phone = wechatAuthService.resolvePhoneNumber(request.phoneCode());
        SysUser user = userService.login(identity.openid(), identity.unionid(), request.nickname(), phone);
        String token = jwtTokenService.issueUser(user.getId(), user.getOpenid());
        return ApiResponse.ok(new LoginResponse(user.getId(), user.getNickname(), user.getPhone(),
                user.getAvatarUrl(), token));
    }

    @PostMapping("/sms-code")
    @Operation(summary = "发送短信验证码（测试服务）", description = "测试短信服务不真实发送短信：验证码打印到服务端日志，并在响应 testCode 字段回显，便于联调。60 秒内限重发，验证码 5 分钟有效。")
    public ApiResponse<SmsAuthService.SendResult> sendSmsCode(@RequestBody SmsCodeRequest request) {
        String phone = request == null ? null : request.phone();
        return ApiResponse.ok(smsAuthService.sendCode(phone));
    }

    @PostMapping("/login-sms")
    @Operation(summary = "短信验证码登录/注册", description = "校验短信验证码；手机号首次使用时自动注册账号（openid 留空，后续可再绑定微信）。")
    public ApiResponse<LoginResponse> loginBySms(@RequestBody SmsLoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        smsAuthService.verifyAndConsume(request.phone(), request.code());
        SysUser user = userService.loginByPhone(request.phone(), request.nickname());
        String subject = user.getOpenid() != null ? user.getOpenid() : "sms:" + user.getId();
        String token = jwtTokenService.issueUser(user.getId(), subject);
        return ApiResponse.ok(new LoginResponse(user.getId(), user.getNickname(), user.getPhone(),
                user.getAvatarUrl(), token));
    }

    @GetMapping("/profile")
    @Operation(summary = "查询当前用户资料", description = "返回 JWT 对应用户的昵称、头像与手机号。")
    public ApiResponse<ProfileResponse> profile() {
        return ApiResponse.ok(ProfileResponse.from(userService.requireById(AuthContext.requireUserId())));
    }

    @PutMapping("/profile")
    @Operation(summary = "更新当前用户资料", description = "更新昵称和头像地址；未提供的字段保持原值。")
    public ApiResponse<ProfileResponse> updateProfile(@RequestBody UpdateProfileRequest request) {
        String nickname = request == null ? null : request.nickname();
        String avatarUrl = request == null ? null : request.avatarUrl();
        SysUser user = userService.updateProfile(AuthContext.requireUserId(), nickname, avatarUrl);
        return ApiResponse.ok(ProfileResponse.from(user));
    }

    @PostMapping("/avatar")
    @Operation(summary = "上传当前用户头像", description = "接收 wx.chooseAvatar 返回的临时文件，保存后更新用户头像地址。")
    public ApiResponse<ProfileResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = AuthContext.requireUserId();
        String avatarUrl = avatarStorageService.save(userId, file);
        SysUser user = userService.updateProfile(userId, null, avatarUrl);
        return ApiResponse.ok(ProfileResponse.from(user));
    }

    public record LoginRequest(@Schema(description = "微信 wx.login 返回的临时 code", example = "code_example") String code,
                               @Schema(description = "仅在 WECHAT_DEBUG_OPENID_ENABLED=true 时可用的服务端调试字段") String openid,
                               @Schema(description = "用户昵称", example = "张三") String nickname,
                               @Schema(description = "手机号", example = "13800000000") String phone) {
    }

    public record LoginResponse(Long userId, String nickname, String phone, String avatarUrl, String token) {
    }

    public record UpdateProfileRequest(@Schema(description = "用户昵称", example = "张三") String nickname,
                                       @Schema(description = "头像地址，通常为 /avatar 上传接口返回的路径") String avatarUrl) {
    }

    public record PhoneLoginRequest(@Schema(description = "微信 wx.login 返回的临时 code") String loginCode,
                                    @Schema(description = "getPhoneNumber 按钮返回的手机号动态令牌") String phoneCode,
                                    @Schema(description = "用户昵称，可选") String nickname) {
    }

    public record SmsCodeRequest(@Schema(description = "手机号", example = "13800000000") String phone) {
    }

    public record SmsLoginRequest(@Schema(description = "手机号", example = "13800000000") String phone,
                                  @Schema(description = "短信验证码", example = "123456") String code,
                                  @Schema(description = "用户昵称，可选") String nickname) {
    }

    public record ProfileResponse(Long userId, String nickname, String phone, String avatarUrl) {
        static ProfileResponse from(SysUser user) {
            return new ProfileResponse(user.getId(), user.getNickname(), user.getPhone(), user.getAvatarUrl());
        }
    }
}
