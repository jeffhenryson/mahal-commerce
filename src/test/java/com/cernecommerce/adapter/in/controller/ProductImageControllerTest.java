package com.cernecommerce.adapter.in.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.core.domain.model.storage.FileServeResult;
import com.cernecommerce.core.ports.in.ProductImageUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductImageControllerTest {

    private MockMvc mockMvc;
    private ProductImageUseCase productImageUseCase;

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    @BeforeEach
    void setup() {
        productImageUseCase = mock(ProductImageUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProductImageController(productImageUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void serve_local_file_returns_200_with_cache_headers() throws Exception {
        when(productImageUseCase.serve("uuid.jpg"))
                .thenReturn(new FileServeResult.LocalFile(JPEG_BYTES, "jpg"));

        mockMvc.perform(get("/product-images/uuid.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=")));
    }

    @Test
    void serve_s3_file_returns_redirect() throws Exception {
        when(productImageUseCase.serve("uuid.jpg"))
                .thenReturn(new FileServeResult.Redirect("https://cdn.example.com/uuid.jpg"));

        mockMvc.perform(get("/product-images/uuid.jpg"))
                .andExpect(status().is(308))
                .andExpect(header().string("Location", "https://cdn.example.com/uuid.jpg"));
    }

    @Test
    void serve_missing_file_returns_404() throws Exception {
        when(productImageUseCase.serve("missing.jpg"))
                .thenReturn(new FileServeResult.NotFound());

        mockMvc.perform(get("/product-images/missing.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void serve_filename_with_dotdot_returns_404() throws Exception {
        // "..secret.jpg" contém ".." — deve ser rejeitado pela guarda no controller
        mockMvc.perform(get("/product-images/..secret.jpg"))
                .andExpect(status().isNotFound());
    }
}
