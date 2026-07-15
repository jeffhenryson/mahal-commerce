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
public class StatsControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void get_stats_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_stats_with_only_user_read_returns_403() throws Exception {
        mockMvc.perform(get("/stats")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("USER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_stats_with_only_role_read_returns_403() throws Exception {
        mockMvc.perform(get("/stats")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_stats_with_user_read_and_role_read_returns_200() throws Exception {
        mockMvc.perform(get("/stats")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_READ"),
                        new SimpleGrantedAuthority("ROLE_READ"))))
                .andExpect(status().isOk());
    }
}
