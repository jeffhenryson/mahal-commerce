package com.security_spring.adapter.in.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
@SpringBootTest
@ActiveProfiles("dev")
public class UserControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

        @Test
        void assign_role_requires_admin_returns_403_for_USER() throws Exception {
        mockMvc.perform(post("/users/john/roles/ROLE_USER")
            .with(user("bob").roles("USER")))
            .andExpect(status().isForbidden());
        }

        @Test
        void admin_can_create_user_and_assign_role() throws Exception {
        // create user as ADMIN
        String body = "{\"username\":\"john\",\"password\":\"secret123\"}";
        mockMvc.perform(post("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(user("admin").roles("ADMIN")))
            .andExpect(status().isCreated());

        // assign role as ADMIN (should succeed even if role already exists or gets created)
        mockMvc.perform(post("/users/john/roles/ROLE_USER")
            .with(user("admin").roles("ADMIN")))
            .andExpect(status().isNoContent());
        }

    @Test
    void post_users_requires_admin_role_returns_403_for_USER() throws Exception {
        String body = "{\"username\":\"userxyz\",\"password\":\"secret123\"}";
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("bob").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
