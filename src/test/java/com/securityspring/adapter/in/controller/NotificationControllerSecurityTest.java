package com.securityspring.adapter.in.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
class NotificationControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void listar_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unread_count_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mark_as_read_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(patch("/notifications/1/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mark_all_read_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(patch("/notifications/read-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(delete("/notifications/1"))
                .andExpect(status().isUnauthorized());
    }
}
