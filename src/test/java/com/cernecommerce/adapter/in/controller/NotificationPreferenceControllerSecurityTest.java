package com.cernecommerce.adapter.in.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
class NotificationPreferenceControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void get_preferences_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(get("/notifications/preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void put_preference_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(put("/notifications/preferences/PASSWORD_CHANGED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inAppEnabled\":false,\"emailEnabled\":true}"))
                .andExpect(status().isUnauthorized());
    }
}
