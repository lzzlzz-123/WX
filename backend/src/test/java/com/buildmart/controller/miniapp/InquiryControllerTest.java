package com.buildmart.controller.miniapp;

import com.buildmart.common.AuthContext;
import com.buildmart.config.ApiExceptionHandler;
import com.buildmart.entity.Inquiry;
import com.buildmart.mapper.InquiryMapper;
import com.buildmart.service.InquiryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InquiryControllerTest {
    private MockMvc mockMvc;

    @Mock
    private InquiryMapper inquiryMapper;

    @Mock
    private InquiryService inquiryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(inquiryMapper.insert(any(Inquiry.class))).thenReturn(1);
        mockMvc = MockMvcBuilders.standaloneSetup(new InquiryController(inquiryMapper, inquiryService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createBindsUserAndGeneratesInquiryNumber() throws Exception {
        // Standalone MockMvc has no JWT interceptor; simulate an authenticated user.
        AuthContext.setUserId(42L);
        mockMvc.perform(post("/miniapp/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"螺纹钢\",\"quantity\":10,\"unit\":\"吨\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.inquiryNo").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("PENDING_QUOTE"));

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryMapper).insert(captor.capture());
        assertEquals(42L, captor.getValue().getUserId());
        assertNotNull(captor.getValue().getCreatedAt());
    }

    @Test
    void createRejectsUnauthenticatedRequest() throws Exception {
        // No AuthContext set and no legacy X-User-Id header fallback: must fail.
        mockMvc.perform(post("/miniapp/inquiries")
                        .header("X-User-Id", "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"螺纹钢\",\"quantity\":10,\"unit\":\"吨\"}"))
                .andExpect(jsonPath("$.code").value(-1203));
    }
}
