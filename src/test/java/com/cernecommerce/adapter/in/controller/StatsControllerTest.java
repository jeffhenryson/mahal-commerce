package com.cernecommerce.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.core.domain.model.StatsResult;
import com.cernecommerce.core.ports.in.StatsUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class StatsControllerTest {

    private MockMvc mockMvc;
    private StatsUseCase statsUseCase;

    @BeforeEach
    void setup() {
        statsUseCase = mock(StatsUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StatsController(statsUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void stats_returns_200_with_all_counts() throws Exception {
        when(statsUseCase.getStats())
                .thenReturn(new StatsResult(100L, 85L, 15L, 5L, 20L));

        mockMvc.perform(get("/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(100))
                .andExpect(jsonPath("$.activeUsers").value(85))
                .andExpect(jsonPath("$.disabledUsers").value(15))
                .andExpect(jsonPath("$.totalRoles").value(5))
                .andExpect(jsonPath("$.totalPermissions").value(20));

        verify(statsUseCase).getStats();
    }

    @Test
    void stats_returns_zeros_when_no_data() throws Exception {
        when(statsUseCase.getStats())
                .thenReturn(new StatsResult(0L, 0L, 0L, 0L, 0L));

        mockMvc.perform(get("/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(0))
                .andExpect(jsonPath("$.disabledUsers").value(0));
    }
}
