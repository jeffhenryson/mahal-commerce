package com.security_spring.adapter.in.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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
    void assign_role_requires_permission_returns_403_for_USER() throws Exception {
        mockMvc.perform(post("/users/john/roles/ROLE_USER")
            .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"),
                                          new SimpleGrantedAuthority("USER_READ"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_create_user_and_assign_role() throws Exception {
        String body = "{\"username\":\"john\",\"password\":\"Secret@123\"}";
        mockMvc.perform(post("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(user("admin").authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("USER_CREATE"),
                new SimpleGrantedAuthority("USER_READ"),
                new SimpleGrantedAuthority("USER_ROLE_ASSIGN"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/users/john/roles/ROLE_USER")
            .with(user("admin").authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("USER_ROLE_ASSIGN"))))
            .andExpect(status().isNoContent());
    }

    @Test
    void post_users_requires_create_permission_returns_403_for_USER() throws Exception {
        String body = "{\"username\":\"userxyz\",\"password\":\"Secret@123\"}";
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"),
                                              new SimpleGrantedAuthority("USER_READ"))))
                .andExpect(status().isForbidden());
    }
}
