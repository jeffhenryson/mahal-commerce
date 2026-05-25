package com.securityspring.adapter.in.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.securityspring.infra.security.support.RefreshTokenTestHelper;
import com.securityspring.infra.security.support.TestHashUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("dev")
public class UserProfileAndSessionsTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private RefreshTokenTestHelper refreshTokenTestHelper;

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void me_returns_own_profile_of_authenticated_user() throws Exception {
        mockMvc.perform(get("/users/me")
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void me_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void change_own_password_success_returns_204() throws Exception {
        // Create a dedicated test user to avoid state pollution across tests
        String uniqueUsername = "pwtest_" + System.currentTimeMillis();
        String createBody = String.format(
                "{\"username\":\"%s\",\"password\":\"Pass1@word\"}", uniqueUsername);

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated());

        // Change that user's password
        String changeBody = "{\"currentPassword\":\"Pass1@word\",\"newPassword\":\"NewPass@999\"}";
        mockMvc.perform(put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeBody)
                .with(user(uniqueUsername).authorities(
                        new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void change_own_password_wrong_current_returns_400() throws Exception {
        String body = "{\"currentPassword\":\"TOTALLY_WRONG\",\"newPassword\":\"NewPass@123\"}";
        mockMvc.perform(put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void change_own_password_new_password_too_short_returns_400() throws Exception {
        String body = "{\"currentPassword\":\"Admin@dev1\",\"newPassword\":\"short\"}";
        mockMvc.perform(put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void simple_logout_blocks_access_token_immediately() throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"User@dev1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = om.readTree(r.getResponse().getContentAsString());
        String accessToken = json.get("accessToken").asText();
        String refreshToken = json.get("refreshToken").asText();

        // Single-device logout: only the refresh token is sent
        mockMvc.perform(post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        // Access token must be blocklisted immediately after logout
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void password_change_blocks_previously_issued_access_token() throws Exception {
        // Create a dedicated user to avoid state pollution
        String username = "pwblock_" + System.currentTimeMillis();
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1@word\"}", username))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated());

        // Login to get an access token
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1@word\"}", username)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();

        // Change password — must invalidate all existing sessions
        mockMvc.perform(put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Pass1@word\",\"newPassword\":\"NewPass@999\"}")
                .with(user(username).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNoContent());

        // Old access token must now be blocked
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_all_sessions_revokes_tokens_and_returns_204() throws Exception {
        // Login as user (not admin, to avoid affecting other tests that use admin tokens)
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"User@dev1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = om.readTree(r.getResponse().getContentAsString());
        String accessToken = json.get("accessToken").asText();

        // Revoke all sessions — must succeed
        mockMvc.perform(delete("/auth/sessions")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // The same access token is now blocklisted — subsequent request must return 401
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }
}
