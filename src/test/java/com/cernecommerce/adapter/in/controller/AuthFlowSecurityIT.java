package com.cernecommerce.adapter.in.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cernecommerce.infra.security.support.RefreshTokenTestHelper;
import com.cernecommerce.infra.security.support.SeedCredentials;
import com.cernecommerce.infra.security.support.TestHashUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        String username = "john_" + uniqueSuffix;
        String email = "john_" + uniqueSuffix + "@test.com";
        String body = String.format("{\"username\":\"%s\",\"password\":\"Secret@123\",\"email\":\"%s\"}", username, email);
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
        mockMvc.perform(post("/users/" + username + "/roles/ROLE_USER")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin")
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),
                                     new SimpleGrantedAuthority("USER_ROLE_ASSIGN"))))
                .andExpect(status().isNoContent());

        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Secret@123\"}", username)))
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
                .content("{\"username\":\"" + SeedCredentials.SEED_ADMIN_USERNAME + "\",\"password\":\"" + SeedCredentials.SEED_ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = om.readTree(r.getResponse().getContentAsString());
        String refresh = json.get("refreshToken").asText();

        // Expire the refresh token via test helper (uses JDBC — sem acoplar ao JPA repo de produção)
        refreshTokenTestHelper.expireTokenByHash(sha256(refresh));

        // Attempt refresh -> should be 401 (expired session — forces re-login)
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DirtiesContext
    void concurrent_refresh_with_same_token_succeeds_exactly_once() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + SeedCredentials.SEED_ADMIN_USERNAME
                        + "\",\"password\":\"" + SeedCredentials.SEED_ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String refresh = om.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures  = new AtomicInteger(0);
        String body = "{\"refreshToken\":\"" + refresh + "\"}";

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    MvcResult result = mockMvc.perform(post("/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) successes.incrementAndGet();
                    else failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
                return null;
            }));
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        for (Future<?> f : futures) f.get(); // propagate exceptions

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);
    }

    private static String sha256(String value) {
        return TestHashUtils.sha256(value);
    }
}
