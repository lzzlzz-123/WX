package com.buildmart.controller.miniapp;

import com.buildmart.common.PageResult;
import com.buildmart.entity.Product;
import com.buildmart.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogControllerTest {
    private MockMvc mockMvc;

    @Mock
    private ProductService productService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new CatalogController(productService)).build();
    }

    @Test
    void productsOnlyQueriesActiveProducts() throws Exception {
        Product product = new Product();
        product.setId(1L);
        product.setSku("SN-PO425-50");
        product.setName("普通硅酸盐水泥");
        when(productService.page(1L, 20L, "水泥", null, null, 1))
                .thenReturn(new PageResult<>(List.of(product), 1L, 1L, 20L));

        mockMvc.perform(get("/miniapp/catalog/products")
                        .param("page", "1")
                        .param("size", "20")
                        .param("keyword", "水泥"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].sku").value("SN-PO425-50"))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(productService).page(1L, 20L, "水泥", null, null, 1);
    }
}
