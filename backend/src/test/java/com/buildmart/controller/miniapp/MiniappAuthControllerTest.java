package com.buildmart.controller.miniapp;

import com.buildmart.common.AuthException;
import com.buildmart.config.ApiExceptionHandler;
import com.buildmart.entity.SysUser;
import com.buildmart.service.AvatarStorageService;
import com.buildmart.service.JwtTokenService;
import com.buildmart.service.SmsAuthService;
import com.buildmart.service.UserService;
import com.buildmart.service.WechatAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MiniappAuthControllerTest {
    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @Mock
    private WechatAuthService wechatAuthService;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private AvatarStorageService avatarStorageService;

    @Mock
    private SmsAuthService smsAuthService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MiniappAuthController(userService, wechatAuthService, jwtTokenService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void loginExchangesWechatCodeBeforeCreatingUser() throws Exception {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setNickname("微信用户");
        user.setOpenid("openid-7");
        when(wechatAuthService.resolveIdentity("wx-code"))
                .thenReturn(new WechatAuthService.WechatIdentity("openid-7", "unionid-7"));
        when(userService.login("openid-7", "unionid-7", "微信用户", null)).thenReturn(user);
        when(jwtTokenService.issueUser(7L, "openid-7")).thenReturn("user-jwt");

        mockMvc.perform(post("/miniapp/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"wx-code\",\"nickname\":\"微信用户\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.nickname").value("微信用户"))
                .andExpect(jsonPath("$.data.token").value("user-jwt"));

        verify(wechatAuthService).resolveIdentity("wx-code");
        verify(userService).login("openid-7", "unionid-7", "微信用户", null);
    }

    @Test
    void missingCodeReturnsStableAuthError() throws Exception {
        mockMvc.perform(post("/miniapp/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"微信用户\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(AuthException.Reason.CODE_REQUIRED.apiCode()))
                .andExpect(jsonPath("$.message").value("微信登录 code 不能为空"));

        verify(wechatAuthService, never()).resolveIdentity(any());
        verify(userService, never()).login(any(), any(), any(), any());
    }

    @Test
    void rejectsExplicitOpenidWhenDebugModeIsDisabled() throws Exception {
        mockMvc.perform(post("/miniapp/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openid\":\"openid-debug\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(AuthException.Reason.OPENID_NOT_ALLOWED.apiCode()));

        verify(wechatAuthService, never()).resolveIdentity(any());
        verify(userService, never()).login(any(), any(), any(), any());
    }

    @Test
    void acceptsExplicitOpenidOnlyWhenDebugModeIsEnabled() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MiniappAuthController(userService, wechatAuthService, jwtTokenService, avatarStorageService, smsAuthService, true))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        SysUser user = new SysUser();
        user.setId(8L);
        user.setOpenid("openid-debug");
        user.setNickname("调试用户");
        when(userService.login("openid-debug", null, "调试用户", null)).thenReturn(user);
        when(jwtTokenService.issueUser(8L, "openid-debug")).thenReturn("debug-jwt");

        mockMvc.perform(post("/miniapp/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openid\":\"openid-debug\",\"nickname\":\"调试用户\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(8))
                .andExpect(jsonPath("$.data.token").value("debug-jwt"));

        verify(wechatAuthService, never()).resolveIdentity(any());
        verify(userService).login("openid-debug", null, "调试用户", null);
    }
}
