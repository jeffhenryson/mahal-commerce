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
public class PermissionControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void list_permissions_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/permissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_permissions_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/permissions")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_permissions_with_permission_read_returns_200() throws Exception {
        mockMvc.perform(get("/permissions")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PERMISSION_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_permission_without_permission_create_returns_403() throws Exception {
        String body = "{\"name\":\"PERM_TEST_" + System.currentTimeMillis() + "\"}";
        mockMvc.perform(post("/permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("PERMISSION_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_permission_with_dev_permission_manage_returns_201() throws Exception {
        String name = "PERM_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")
                .with(user("dev").authorities(
                        new SimpleGrantedAuthority("ROLE_DEV"),
                        new SimpleGrantedAuthority("DEV_ELEVATED"),
                        new SimpleGrantedAuthority("DEV_PERMISSION_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void delete_permission_without_permission_delete_returns_403() throws Exception {
        mockMvc.perform(delete("/permissions/SOME_PERM")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("PERMISSION_READ"))))
                .andExpect(status().isForbidden());
    }
}
