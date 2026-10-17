package com.buildmart.controller.admin;

import com.buildmart.entity.SysAdmin;
import com.buildmart.mapper.SysAdminMapper;
import com.buildmart.service.JwtTokenService;
import com.buildmart.config.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAuthControllerTest {
    private MockMvc mockMvc;

    @Mock
    private SysAdminMapper adminMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAuthController(adminMapper, passwordEncoder, jwtTokenService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void loginReturnsJwtForValidCredentials() throws Exception {
        SysAdmin admin = new SysAdmin();
        admin.setId(10L);
        admin.setUsername("admin");
        admin.setPasswordHash("encoded-password");
        admin.setRealName("系统管理员");
        admin.setRoleCode("SUPER_ADMIN");
        admin.setStatus(1);
        when(adminMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.matches(eq("password"), eq("encoded-password"))).thenReturn(true);
        when(jwtTokenService.issue(10L, "admin", "SUPER_ADMIN")).thenReturn("jwt-token");

        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.roleCode").value("SUPER_ADMIN"));
    }

    @Test
    void loginReturnsErrorForUnknownCredentials() throws Exception {
        when(adminMapper.selectOne(any())).thenReturn(null);

        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"unknown\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }
}
