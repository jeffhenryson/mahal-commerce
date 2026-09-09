package com.cernecommerce.infra.security;

import com.cernecommerce.core.ports.out.SystemConfigPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaintenanceModeFilterTest {

    @RestController
    static class StubController {
        @GetMapping("/api/users")                public void users()   {}
        @GetMapping("/actuator/health")          public void health()  {}
        @GetMapping("/actuator/health/liveness") public void liveness(){}
        @GetMapping("/system/config/public")     public void config()  {}
    }

    private SystemConfigPort systemConfig;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        systemConfig = mock(SystemConfigPort.class);
        MaintenanceModeFilter filter = new MaintenanceModeFilter(systemConfig);
        mvc = MockMvcBuilders
                .standaloneSetup(new StubController())
                .addFilter(filter)
                .build();
    }

    @Test
    void maintenance_off_passes_all_requests() throws Exception {
        when(systemConfig.getBoolean("security.maintenance.enabled", false)).thenReturn(false);

        mvc.perform(get("/api/users")).andExpect(status().isOk());
    }

    @Test
    void maintenance_on_blocks_regular_endpoints_with_503() throws Exception {
        when(systemConfig.getBoolean("security.maintenance.enabled", false)).thenReturn(true);

        mvc.perform(get("/api/users"))
                .andExpect(status().isServiceUnavailable())
                // contentTypeCompatibleWith, não contentType: o filtro manda
                // "application/json;charset=UTF-8" (PLAT-C050) e a comparação exata nunca casaria.
                // Mesmo matcher de LoginRateLimitingFilterTest para o filtro irmão.
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
    }

    /**
     * PLAT-C050 — o filtro escreve o corpo direto no response com
     * {@code MediaType.APPLICATION_JSON_VALUE}, que é "application/json" seco, e sem
     * {@code setCharacterEncoding} o {@code getWriter()} do Tomcat cai no default do container: a
     * mensagem chegava ao cliente como {@code "Sistema em manuten??o ? tente novamente em breve"}.
     * Este teste falha se alguém remover o charset do filtro.
     */
    @Test
    void maintenance_on_preservaAcentuacaoNoCorpoDoErro() throws Exception {
        when(systemConfig.getBoolean("security.maintenance.enabled", false)).thenReturn(true);

        mvc.perform(get("/api/users"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("Sistema em manutenção — tente novamente em breve"));
    }

    @Test
    void maintenance_on_allows_actuator_health() throws Exception {
        when(systemConfig.getBoolean("security.maintenance.enabled", false)).thenReturn(true);

        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void maintenance_on_allows_actuator_health_subpath() throws Exception {
        when(systemConfig.getBoolean("security.maintenance.enabled", false)).thenReturn(true);

        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void maintenance_on_allows_system_config_public() throws Exception {
        when(systemConfig.getBoolean("security.maintenance.enabled", false)).thenReturn(true);

        mvc.perform(get("/system/config/public")).andExpect(status().isOk());
    }
}
