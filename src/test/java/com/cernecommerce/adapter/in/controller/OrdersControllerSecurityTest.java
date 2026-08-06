package com.cernecommerce.adapter.in.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.adapter.out.persistence.repository.AuditLogJpaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("dev")
public class OrdersControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AuditLogJpaRepository auditRepo;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void awaitAction(String action) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (auditRepo.findAll().stream().anyMatch(e -> action.equals(e.getAction()))) return;
            Thread.sleep(50);
        }
        assertThat(auditRepo.findAll().stream().anyMatch(e -> action.equals(e.getAction())))
                .as("Expected audit entry with action=" + action).isTrue();
    }

    @Test
    void list_orders_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_orders_without_order_read_returns_403() throws Exception {
        mockMvc.perform(get("/orders")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_orders_with_order_read_returns_200() throws Exception {
        mockMvc.perform(get("/orders")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void get_order_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/orders/999999"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_order_without_order_read_returns_403() throws Exception {
        mockMvc.perform(get("/orders/999999")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_order_with_order_read_returns_404_for_inexistent() throws Exception {
        mockMvc.perform(get("/orders/999999")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void change_status_without_order_fulfill_returns_403() throws Exception {
        mockMvc.perform(post("/orders/999999/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ENVIADO\"}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void change_status_with_order_fulfill_returns_404_for_inexistent() throws Exception {
        mockMvc.perform(post("/orders/999999/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ENVIADO\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ORDER_FULFILL"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_order_without_order_cancel_returns_403() throws Exception {
        mockMvc.perform(post("/orders/999999/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Motivo\"}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_order_with_order_cancel_returns_404_for_inexistent() throws Exception {
        mockMvc.perform(post("/orders/999999/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Motivo\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ORDER_CANCEL"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void refund_order_without_order_refund_returns_403() throws Exception {
        mockMvc.perform(post("/orders/999999/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Motivo\"}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void refund_order_with_order_refund_returns_404_for_inexistent() throws Exception {
        mockMvc.perform(post("/orders/999999/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Motivo\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ORDER_REFUND"))))
                .andExpect(status().isNotFound());
    }
}
