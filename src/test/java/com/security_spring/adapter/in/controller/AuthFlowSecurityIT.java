package com.security_spring.adapter.in.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.security_spring.adapter.out.repository.RefreshTokenJpaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@ActiveProfiles("dev")
public class AuthFlowSecurityIT {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private RefreshTokenJpaRepository refreshRepo;

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void user_role_can_read_with_bearer() throws Exception {
        // Create user as ADMIN
        String body = "{\"username\":\"john2\",\"password\":\"secret123\"}";
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isCreated());

        // Assign ROLE_USER as ADMIN
        mockMvc.perform(post("/users/john2/roles/ROLE_USER")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        // Login as john2
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john2\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = om.readTree(r.getResponse().getContentAsString());
        String access = json.get("accessToken").asText();

        // Access GET /users with bearer -> 200
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_expired_returns_400() throws Exception {
        // Login as admin (seeded in dev) to get a refresh token
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = om.readTree(r.getResponse().getContentAsString());
        String refresh = json.get("refreshToken").asText();

        // Expire the refresh token in DB
        String hash = sha256(refresh);
        var row = refreshRepo.findByTokenHash(hash).orElseThrow();
        row.setExpiresAt(Instant.now().minusSeconds(5));
        refreshRepo.save(row);

        // Attempt refresh -> should be 400 (expired)
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private static String sha256(String value) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
