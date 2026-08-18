package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class FinanceiroControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper om = new ObjectMapper();

    private static final String VALID_BODY = """
            {
              "description": "Aluguel Loja Julho/2026",
              "entityName": "Imobiliária Central RJ",
              "category": "ALUGUEL",
              "direction": "OUTFLOW",
              "amount": 8500.0,
              "dueDate": "2026-07-10"
            }
            """;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ── GET /financeiro/cash-flow ───────────────────────────────────────────

    @Test
    void list_cash_flow_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/financeiro/cash-flow"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_cash_flow_without_financeiro_read_returns_403() throws Exception {
        mockMvc.perform(get("/financeiro/cash-flow")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_cash_flow_with_financeiro_read_returns_200() throws Exception {
        mockMvc.perform(get("/financeiro/cash-flow")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("FINANCEIRO_READ"))))
                .andExpect(status().isOk());
    }

    // ── GET /financeiro/cash-flow/summary ───────────────────────────────────

    @Test
    void summary_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/financeiro/cash-flow/summary")
                        .param("from", "2026-07-01").param("to", "2026-07-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summary_with_financeiro_read_returns_200() throws Exception {
        mockMvc.perform(get("/financeiro/cash-flow/summary")
                        .param("from", "2026-07-01").param("to", "2026-07-31")
                        .with(user("gerente").authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("FINANCEIRO_READ"))))
                .andExpect(status().isOk());
    }

    // ── POST /financeiro/cash-flow ──────────────────────────────────────────

    @Test
    void create_cash_flow_entry_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/financeiro/cash-flow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_cash_flow_entry_without_manage_returns_403() throws Exception {
        mockMvc.perform(post("/financeiro/cash-flow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(user("gerente").authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("FINANCEIRO_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_cash_flow_entry_with_manage_returns_201() throws Exception {
        mockMvc.perform(post("/financeiro/cash-flow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(user("gerente").authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("FINANCEIRO_CASH_FLOW_MANAGE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PREVISTO"));
    }

    // ── PATCH /financeiro/cash-flow/{id} ────────────────────────────────────

    @Test
    void patch_cash_flow_entry_without_auth_returns_401() throws Exception {
        mockMvc.perform(patch("/financeiro/cash-flow/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAGO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patch_cash_flow_entry_without_manage_returns_403() throws Exception {
        mockMvc.perform(patch("/financeiro/cash-flow/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAGO\"}")
                        .with(user("gerente").authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("FINANCEIRO_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_cash_flow_entry_with_manage_returns_200() throws Exception {
        var manage = user("gerente").authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("FINANCEIRO_CASH_FLOW_MANAGE"));

        String createResponse = mockMvc.perform(post("/financeiro/cash-flow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(manage))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = om.readTree(createResponse).get("id").asLong();

        mockMvc.perform(patch("/financeiro/cash-flow/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAGO\"}")
                        .with(manage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGO"))
                .andExpect(jsonPath("$.paymentDate").exists());
    }

    // ── DELETE /financeiro/cash-flow/{id} ───────────────────────────────────

    @Test
    void delete_cash_flow_entry_without_auth_returns_401() throws Exception {
        mockMvc.perform(delete("/financeiro/cash-flow/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_cash_flow_entry_without_manage_returns_403() throws Exception {
        mockMvc.perform(delete("/financeiro/cash-flow/1")
                        .with(user("gerente").authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("FINANCEIRO_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_cash_flow_entry_with_manage_returns_204() throws Exception {
        var manage = user("gerente").authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("FINANCEIRO_CASH_FLOW_MANAGE"));

        String createResponse = mockMvc.perform(post("/financeiro/cash-flow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(manage))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = om.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/financeiro/cash-flow/" + id).with(manage))
                .andExpect(status().isNoContent());
    }
}
