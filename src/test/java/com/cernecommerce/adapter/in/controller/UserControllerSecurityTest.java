package com.cernecommerce.adapter.in.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cernecommerce.adapter.out.security.blocklist.InMemoryTokenBlocklistAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
@SpringBootTest
@ActiveProfiles("dev")
public class UserControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InMemoryTokenBlocklistAdapter blocklistAdapter;
    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        blocklistAdapter.clearAll();
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
        String body = "{\"username\":\"john\",\"password\":\"Secret@123\",\"email\":\"john@test.com\"}";
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

    /**
     * Sem esta checagem, USER_CREATE sozinho bastaria pra criar um usuário já nascendo ROLE_DEV,
     * contornando a barreira elevada que assign_role_to_ROLE_DEV_without_dev_elevation_returns_403
     * prova logo abaixo para o endpoint de atribuição avulsa — as duas portas para o mesmo cofre
     * precisam da mesma fechadura.
     */
    @Test
    void create_user_with_role_dev_without_dev_elevation_returns_403() throws Exception {
        String username = "wannabe_dev_" + System.currentTimeMillis();
        String body = String.format(
                "{\"username\":\"%s\",\"password\":\"Secret@123\",\"email\":\"%s@test.com\",\"roles\":[\"ROLE_DEV\"]}",
                username, username);
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_user_with_role_dev_with_dev_elevation_returns_201() throws Exception {
        String username = "real_dev_" + System.currentTimeMillis();
        String body = String.format(
                "{\"username\":\"%s\",\"password\":\"Secret@123\",\"email\":\"%s@test.com\",\"roles\":[\"ROLE_DEV\"]}",
                username, username);
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("dev-admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"),
                        new SimpleGrantedAuthority("DEV_ROLE_MANAGE"),
                        new SimpleGrantedAuthority("DEV_ELEVATED"))))
                .andExpect(status().isCreated());
    }

    @Test
    void assign_role_to_ROLE_DEV_without_dev_elevation_returns_403() throws Exception {
        mockMvc.perform(post("/users/john/roles/ROLE_DEV")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_ROLE_ASSIGN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_users_requires_create_permission_returns_403_for_USER() throws Exception {
        String body = "{\"username\":\"userxyz\",\"password\":\"Secret@123\",\"email\":\"userxyz@test.com\"}";
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"),
                                              new SimpleGrantedAuthority("USER_READ"))))
                .andExpect(status().isForbidden());
    }

    /** Cria um usuário fresco via HTTP (mesmo padrão de admin_can_create_user_and_assign_role) e devolve o id. */
    private Long givenUser() throws Exception {
        String username = "sec_" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Secret@123\",\"email\":\"%s@test.com\"}",
                        username, username))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void get_user_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/users/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_user_without_user_read_returns_403() throws Exception {
        mockMvc.perform(get("/users/1")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_user_with_user_read_returns_200() throws Exception {
        Long id = givenUser();
        mockMvc.perform(get("/users/" + id)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("USER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_users_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_users_without_user_read_returns_403() throws Exception {
        mockMvc.perform(get("/users")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_users_with_user_read_returns_200() throws Exception {
        mockMvc.perform(get("/users")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("USER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void delete_user_without_auth_returns_401() throws Exception {
        mockMvc.perform(delete("/users/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void delete_user_without_user_delete_returns_403() throws Exception {
        mockMvc.perform(delete("/users/1")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_user_with_user_delete_returns_204() throws Exception {
        Long id = givenUser();
        mockMvc.perform(delete("/users/" + id)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("USER_DELETE"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void disable_user_without_auth_returns_401() throws Exception {
        mockMvc.perform(put("/users/1/disable")).andExpect(status().isUnauthorized());
    }

    @Test
    void disable_user_without_user_status_returns_403() throws Exception {
        mockMvc.perform(put("/users/1/disable")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void enable_user_without_auth_returns_401() throws Exception {
        mockMvc.perform(put("/users/1/enable")).andExpect(status().isUnauthorized());
    }

    @Test
    void enable_user_without_user_status_returns_403() throws Exception {
        mockMvc.perform(put("/users/1/enable")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void enable_user_with_user_status_returns_204() throws Exception {
        Long id = givenUser();
        mockMvc.perform(put("/users/" + id + "/enable")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("USER_STATUS"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void update_user_without_auth_returns_401() throws Exception {
        mockMvc.perform(patch("/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_user_without_user_update_returns_403() throws Exception {
        mockMvc.perform(patch("/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"whatever\"}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_user_with_user_update_returns_200() throws Exception {
        Long id = givenUser();
        String newUsername = "updated_" + System.nanoTime();
        mockMvc.perform(patch("/users/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + newUsername + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("USER_UPDATE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(newUsername));
    }

    @Test
    void remove_role_without_auth_returns_401() throws Exception {
        mockMvc.perform(delete("/users/john/roles/ROLE_USER")).andExpect(status().isUnauthorized());
    }

    @Test
    void remove_role_without_user_role_assign_returns_403() throws Exception {
        mockMvc.perform(delete("/users/john/roles/ROLE_USER")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void remove_role_with_user_role_assign_returns_204() throws Exception {
        Long id = givenUser();
        String username = om.readTree(mockMvc.perform(get("/users/" + id)
                        .with(user("gerente").authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("USER_READ"))))
                        .andReturn().getResponse().getContentAsString())
                .get("username").asText();

        mockMvc.perform(post("/users/" + username + "/roles/ROLE_USER")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("USER_ROLE_ASSIGN"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/users/" + username + "/roles/ROLE_USER")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("USER_ROLE_ASSIGN"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void change_own_password_without_auth_returns_401() throws Exception {
        mockMvc.perform(put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"x\",\"newPassword\":\"Secret@123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabled_user_access_token_is_rejected() throws Exception {
        // Create a user, log in, disable them, then verify the access token is blocked.
        String username = "disabled_" + System.currentTimeMillis();
        MvcResult createResult = mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Secret@123\",\"email\":\"%s@test.com\"}", username, username))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"),
                        new SimpleGrantedAuthority("USER_READ"))))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = om.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // Login to get an access token
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Secret@123\",\"email\":\"%s@test.com\"}", username, username)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = om.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Admin disables the user — this must also block all active tokens
        mockMvc.perform(put("/users/" + userId + "/disable")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_STATUS"))))
                .andExpect(status().isNoContent());

        // The old access token must now be rejected
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }
}
