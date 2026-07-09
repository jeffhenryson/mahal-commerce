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
public class RoleControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void list_roles_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/roles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_roles_without_role_read_returns_403() throws Exception {
        mockMvc.perform(get("/roles")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_roles_with_role_read_returns_200() throws Exception {
        mockMvc.perform(get("/roles")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_role_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ROLE_TEST\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_role_without_dev_role_manage_returns_403() throws Exception {
        mockMvc.perform(post("/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ROLE_TEST\"}")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_role_with_dev_role_manage_returns_201() throws Exception {
        String name = "ROLE_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")
                .with(user("dev").authorities(
                        new SimpleGrantedAuthority("ROLE_DEV"),
                        new SimpleGrantedAuthority("DEV_ELEVATED"),
                        new SimpleGrantedAuthority("DEV_ROLE_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void delete_role_without_auth_returns_401() throws Exception {
        mockMvc.perform(delete("/roles/ROLE_NONEXISTENT"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_role_without_dev_role_manage_returns_403() throws Exception {
        mockMvc.perform(delete("/roles/ROLE_NONEXISTENT")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assign_permission_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/roles/ROLE_USER/permissions/USER_READ"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assign_permission_without_role_manage_permissions_returns_403() throws Exception {
        mockMvc.perform(post("/roles/ROLE_USER/permissions/USER_READ")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assign_dev_only_permission_without_dev_elevated_returns_403() throws Exception {
        mockMvc.perform(post("/roles/ROLE_USER/permissions/DEV_ROLE_MANAGE")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_MANAGE_PERMISSIONS"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void remove_dev_only_permission_without_dev_elevated_returns_403() throws Exception {
        mockMvc.perform(delete("/roles/ROLE_USER/permissions/DEV_ROLE_MANAGE")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_MANAGE_PERMISSIONS"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void remove_permission_without_auth_returns_401() throws Exception {
        mockMvc.perform(delete("/roles/ROLE_USER/permissions/USER_READ"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void remove_permission_without_role_manage_permissions_returns_403() throws Exception {
        mockMvc.perform(delete("/roles/ROLE_USER/permissions/USER_READ")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_READ"))))
                .andExpect(status().isForbidden());
    }
}
