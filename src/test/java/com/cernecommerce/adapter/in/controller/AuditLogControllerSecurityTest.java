package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

@SpringBootTest
@ActiveProfiles("dev")
public class AuditLogControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void list_audit_logs_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_audit_logs_without_audit_read_returns_403() throws Exception {
        mockMvc.perform(get("/audit-logs")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_audit_logs_with_audit_read_returns_200() throws Exception {
        mockMvc.perform(get("/audit-logs")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUDIT_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_actions_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/audit-logs/actions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_actions_without_audit_read_returns_403() throws Exception {
        mockMvc.perform(get("/audit-logs/actions")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_actions_with_audit_read_returns_200() throws Exception {
        mockMvc.perform(get("/audit-logs/actions")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUDIT_READ"))))
                .andExpect(status().isOk());
    }
}
