package com.securityspring.adapter.in.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.securityspring.infra.security.support.SeedCredentials;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests that run against a real PostgreSQL instance via Testcontainers.
 * Enable with: ENABLE_TC=true ./mvnw test
 *
 * These tests verify that the security flow works correctly against the same
 * database engine used in production, including Flyway migrations.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
public class AuthFlowPostgresIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        // H2 console and Redis health check are irrelevant against Postgres
        r.add("management.health.redis.enabled", () -> "false");
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void full_auth_flow_login_refresh_logout_against_postgres() throws Exception {
        // Login with seeded admin user
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + SeedCredentials.SEED_ADMIN_USERNAME + "\",\"password\":\"" + SeedCredentials.SEED_ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode tokens = om.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        // Access a protected endpoint with the new token
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));

        // Rotate the refresh token
        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode newTokens = om.readTree(refreshResult.getResponse().getContentAsString());
        String newAccess = newTokens.get("accessToken").asText();
        String newRefresh = newTokens.get("refreshToken").asText();

        // New access token must work
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());

        // Logout using the new refresh token — access token must be blocked
        mockMvc.perform(post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_token_reuse_revokes_all_sessions_against_postgres() throws Exception {
        // Login as user (separate from admin to avoid cross-test interference)
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"User@dev1\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String firstRefresh = om.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // Rotate once — gets a new refresh token
        MvcResult rotatedResult = mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String newAccess = om.readTree(rotatedResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Reuse the old (already rotated) refresh token → theft detected → all sessions revoked
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isUnauthorized());

        // The access token issued during rotation must also be blocked
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_user_and_login_against_postgres() throws Exception {
        String username = "pg_user_" + System.currentTimeMillis();

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Secure@Pass1\"}", username))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated());

        // Assign ROLE_USER so the user can log in and access /users/me
        mockMvc.perform(post("/users/" + username + "/roles/ROLE_USER")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_ROLE_ASSIGN"))))
                .andExpect(status().isNoContent());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Secure@Pass1\"}", username)))
                .andExpect(status().isOk())
                .andReturn();

        String access = om.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }
}
