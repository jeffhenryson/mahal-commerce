package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Testa autorização do endpoint GET /system/info.
 * Requer DEV_ELEVATED — somente access token emitido após duplo TOTP DEV contém essa authority.
 */
@SpringBootTest
@ActiveProfiles("dev")
public class SystemInfoControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ── autenticação ─────────────────────────────────────────────────────────

    @Test
    void get_system_info_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(get("/system/info"))
                .andExpect(status().isUnauthorized());
    }

    // ── autorização ──────────────────────────────────────────────────────────

    @Test
    void get_system_info_com_role_admin_sem_dev_elevated_retorna_403() throws Exception {
        mockMvc.perform(get("/system/info")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_READ"),
                        new SimpleGrantedAuthority("AUDIT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_system_info_com_role_dev_sem_elevacao_retorna_403() throws Exception {
        mockMvc.perform(get("/system/info")
                .with(user("dev").authorities(
                        new SimpleGrantedAuthority("ROLE_DEV"),
                        new SimpleGrantedAuthority("DEV_ROLE_MANAGE"),
                        new SimpleGrantedAuthority("DEV_PERMISSION_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_system_info_com_dev_elevated_retorna_200_com_profile() throws Exception {
        mockMvc.perform(get("/system/info")
                .with(user("dev").authorities(
                        new SimpleGrantedAuthority("ROLE_DEV"),
                        new SimpleGrantedAuthority("DEV_ELEVATED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.profile").isString());
    }

    @Test
    void get_system_info_retorna_perfil_ativo_no_corpo() throws Exception {
        mockMvc.perform(get("/system/info")
                .with(user("dev").authorities(
                        new SimpleGrantedAuthority("ROLE_DEV"),
                        new SimpleGrantedAuthority("DEV_ELEVATED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles").isArray());
    }
}
