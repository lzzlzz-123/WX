package com.buildmart.controller.admin;

import com.buildmart.config.ApiExceptionHandler;
import com.buildmart.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardAdminControllerTest {
    private MockMvc mockMvc;

    @Mock
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardAdminController(dashboardService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void summaryReturnsDatabaseAggregateContract() throws Exception {
        when(dashboardService.summary()).thenReturn(new DashboardService.DashboardSummary(
                4, 1, 12, 3, 2, new BigDecimal("286420.00")));

        mockMvc.perform(get("/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.activeProductCount").value(4))
                .andExpect(jsonPath("$.data.pendingShipmentCount").value(3))
                .andExpect(jsonPath("$.data.orderAmount").value(286420.00));
    }
}
