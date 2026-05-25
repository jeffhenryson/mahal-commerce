package com.securityspring.adapter.in.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securityspring.infra.security.support.RefreshTokenTestHelper;
import com.securityspring.infra.security.support.TestHashUtils;

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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@ActiveProfiles("dev")
public class AuthFlowSecurityIT {

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
    void user_role_can_read_with_bearer() throws Exception {
        // Create user as ADMIN (using permission-based mock)
        String body = "{\"username\":\"john2\",\"password\":\"Secret@123\",\"email\":\"john2@test.com\"}";
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin")
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),
                                     new SimpleGrantedAuthority("USER_CREATE"),
                                     new SimpleGrantedAuthority("USER_READ"),
                                     new SimpleGrantedAuthority("USER_ROLE_ASSIGN"))))
                .andExpect(status().isCreated());

        // Assign ROLE_USER as ADMIN
        mockMvc.perform(post("/users/john2/roles/ROLE_USER")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin")
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),
                                     new SimpleGrantedAuthority("USER_ROLE_ASSIGN"))))
                .andExpect(status().isNoContent());

        // Login as john2 (john2 has ROLE_USER which has USER_READ permission)
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john2\",\"password\":\"Secret@123\"}"))
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
                .content("{\"username\":\"admin\",\"password\":\"Admin@dev1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = om.readTree(r.getResponse().getContentAsString());
        String refresh = json.get("refreshToken").asText();

        // Expire the refresh token via test helper (uses JDBC — sem acoplar ao JPA repo de produção)
        refreshTokenTestHelper.expireTokenByHash(sha256(refresh));

        // Attempt refresh -> should be 400 (expired)
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private static String sha256(String value) {
        return TestHashUtils.sha256(value);
    }
}
