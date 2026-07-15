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
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
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
