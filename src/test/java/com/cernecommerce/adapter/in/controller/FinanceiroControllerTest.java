package com.cernecommerce.adapter.in.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import com.cernecommerce.core.ports.in.FinanceiroUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class FinanceiroControllerTest {

    private MockMvc mockMvc;
    private FinanceiroUseCase financeiroUseCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        financeiroUseCase = mock(FinanceiroUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FinanceiroController(financeiroUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listCashFlow_returns_200() throws Exception {
        CashFlowEntry entry = new CashFlowEntry(1L, LocalDate.now(), "Venda #100", new BigDecimal("150.00"), CashFlowEntry.Direction.INFLOW);
        when(financeiroUseCase.listCashFlow(0, 20))
                .thenReturn(new PageResult<>(List.of(entry), 0, 20, 1L, 1));

        mockMvc.perform(get("/financeiro/cash-flow").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Venda #100"))
                .andExpect(jsonPath("$.content[0].amount").value(150.0))
                .andExpect(jsonPath("$.content[0].direction").value("INFLOW"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
    
    @Test
    void listCashFlow_withPagination_passesParamsThrough() throws Exception {
        when(financeiroUseCase.listCashFlow(1, 10))
                .thenReturn(new PageResult<>(List.of(), 1, 10, 0L, 0));

        mockMvc.perform(get("/financeiro/cash-flow")
                        .principal(AUTH)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }
}
