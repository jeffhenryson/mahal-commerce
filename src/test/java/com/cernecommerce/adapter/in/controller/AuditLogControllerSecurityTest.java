package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.adapter.out.persistence.repository.AuditLogJpaRepository;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
public class AuditLogControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private AuditLogJpaRepository auditRepo;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Aguarda até 2 segundos que o evento DEV_ELEVATION_COMPLETED do username apareça no banco (persistência é async). */
    private void awaitDevElevationEvent(String username) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (auditRepo.findAll().stream().anyMatch(e -> username.equals(e.getUsername())
                    && "DEV_ELEVATION_COMPLETED".equals(e.getAction()))) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(auditRepo.findAll().stream().anyMatch(e -> username.equals(e.getUsername())
                && "DEV_ELEVATION_COMPLETED".equals(e.getAction())))
                .as("Expected DEV_ELEVATION_COMPLETED audit entry for username=" + username).isTrue();
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

    @Test
    void list_audit_logs_without_dev_elevated_hides_dev_only_event() throws Exception {
        String username = "dev_evt_hidden_" + System.currentTimeMillis();
        publisher.publishEvent(AuditEvent.of(EventType.DEV_ELEVATION_COMPLETED, username));
        awaitDevElevationEvent(username);

        mockMvc.perform(get("/audit-logs").param("username", username)
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUDIT_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'DEV_ELEVATION_COMPLETED')]").doesNotExist());
    }

    @Test
    void list_audit_logs_with_dev_elevated_shows_dev_only_event() throws Exception {
        String username = "dev_evt_visible_" + System.currentTimeMillis();
        publisher.publishEvent(AuditEvent.of(EventType.DEV_ELEVATION_COMPLETED, username));
        awaitDevElevationEvent(username);

        mockMvc.perform(get("/audit-logs").param("username", username)
                .with(user("dev").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUDIT_READ"),
                        new SimpleGrantedAuthority("DEV_ELEVATED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'DEV_ELEVATION_COMPLETED')]").exists());
    }
}
