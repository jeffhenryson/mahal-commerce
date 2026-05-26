package com.securityspring.infra.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.securityspring.core.ports.out.ratelimit.LoginRateLimiterPort;
import com.securityspring.adapter.out.security.ratelimit.InMemoryLoginRateLimiterAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AuthRateLimitingTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private LoginRateLimiterPort rateLimiter;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void rateProps(DynamicPropertyRegistry r) {
        r.add("rate.limit.login.window-seconds", () -> 2);
        r.add("rate.limit.login.max-requests", () -> 3);
    }

    @BeforeEach
    void setup() {
        if (rateLimiter instanceof InMemoryLoginRateLimiterAdapter rl) rl.reset();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void returns_429_after_exceeding_login_attempts_in_window() throws Exception {
        String body = "{\"username\":\"nonexistent\",\"password\":\"bad\"}";
        // First three attempts -> unauthenticated (401)
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        // Fourth within window -> 429
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    // /auth/refresh is excluded from IP rate limiting: the opaque refresh token already has
    // reuse detection (rotation invalidates all sessions on reuse). Adding shared-IP rate limits
    // creates NAT false positives without meaningful security benefit.
}
