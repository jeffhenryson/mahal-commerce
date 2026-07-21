package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
public class CrmControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void create_customer_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"contato\":\"11999998888\",\"email\":\"maria@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_customer_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"contato\":\"11999998888\",\"email\":\"maria@example.com\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_customer_with_crm_customer_manage_returns_201() throws Exception {
        String email = "sec_test_" + System.currentTimeMillis() + "@example.com";
        mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"contato\":\"11999998888\",\"email\":\"" + email + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void get_customer_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/customers/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_customer_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/1")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_customer_with_crm_customer_read_returns_200_or_404() throws Exception {
        // Sem cliente prévio cadastrado com esse id, a resposta é 404 (CUSTOMER_NOT_FOUND) —
        // o importante aqui é a authority ser aceita (200/404), não bloqueada por 401/403.
        mockMvc.perform(get("/crm/customers/999999")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_customers_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/customers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_customers_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_customers_with_crm_customer_read_returns_200() throws Exception {
        mockMvc.perform(get("/crm/customers")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void add_note_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/crm/customers/999999/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Nota\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void add_note_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/customers/999999/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Nota\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void add_note_with_crm_customer_manage_returns_201_or_404() throws Exception {
        // Sem cliente prévio cadastrado com esse id, a resposta é 404 (CUSTOMER_NOT_FOUND) —
        // o importante aqui é a authority ser aceita (201/404), não bloqueada por 401/403.
        mockMvc.perform(post("/crm/customers/999999/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Nota\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_notes_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/notes")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_orders_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/orders")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_cashback_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/cashback")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void move_stage_without_auth_returns_401() throws Exception {
        mockMvc.perform(patch("/crm/customers/999999/estagio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estagio\":\"EM_ATENDIMENTO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void move_stage_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(patch("/crm/customers/999999/estagio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estagio\":\"EM_ATENDIMENTO\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void move_stage_with_crm_customer_manage_returns_404_for_inexistent_customer() throws Exception {
        // Sem cliente prévio cadastrado com esse id, a resposta é 404 (CUSTOMER_NOT_FOUND) —
        // o importante aqui é a authority ser aceita (200/404), não bloqueada por 401/403.
        mockMvc.perform(patch("/crm/customers/999999/estagio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estagio\":\"EM_ATENDIMENTO\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_stage_history_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/estagio/historico")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboard_overview_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/dashboard/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboard_overview_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/dashboard/overview")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboard_overview_with_crm_customer_read_returns_200() throws Exception {
        mockMvc.perform(get("/crm/dashboard/overview")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_tag_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/crm/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"VIP\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_tag_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"VIP\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_tag_with_crm_customer_manage_returns_201() throws Exception {
        String nome = "TAG_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/crm/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void list_tags_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/tags"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_tags_with_crm_customer_read_returns_200() throws Exception {
        mockMvc.perform(get("/crm/tags")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void delete_tag_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(delete("/crm/tags/999999")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void add_tag_to_customer_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/customers/999999/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tagId\":999999}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void remove_tag_from_customer_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(delete("/crm/customers/999999/tags/999999")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_customer_tags_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/tags")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void export_customers_csv_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/customers/export"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void export_customers_csv_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/export")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void export_customers_csv_with_crm_customer_read_returns_200() throws Exception {
        mockMvc.perform(get("/crm/customers/export")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_automation_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/crm/automacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Boas-vindas\",\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\","
                        + "\"canal\":\"EMAIL\",\"template\":\"Ola {nome}\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_automation_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/automacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Boas-vindas\",\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\","
                        + "\"canal\":\"EMAIL\",\"template\":\"Ola {nome}\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_automation_with_crm_customer_manage_returns_201() throws Exception {
        String nome = "AUTOMACAO_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/crm/automacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\",\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\","
                        + "\"canal\":\"EMAIL\",\"template\":\"Ola {nome}\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void list_automations_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/automacoes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_automations_with_crm_customer_read_returns_200() throws Exception {
        mockMvc.perform(get("/crm/automacoes")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void set_automation_active_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(patch("/crm/automacoes/999999/ativa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativa\":false}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_automation_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(delete("/crm/automacoes/999999")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatch_automation_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/automacoes/999999/disparar")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatch_automation_with_crm_customer_manage_returns_404_for_inexistent_automation() throws Exception {
        mockMvc.perform(post("/crm/automacoes/999999/disparar")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_automation_log_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/automacoes/999999/log")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }
}
