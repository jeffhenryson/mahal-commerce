package com.cernecommerce.infra.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.core.ports.out.ratelimit.LoginRateLimiterPort;
import com.cernecommerce.adapter.out.security.ratelimit.InMemoryLoginRateLimiterAdapter;
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

    @Test
    void returns_429_after_exceeding_totp_replace_attempts() throws Exception {
        String body = "{\"currentTotpCode\":\"000000\"}";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/2fa/replace").contentType(MediaType.APPLICATION_JSON).content(body));
        }
        mockMvc.perform(post("/auth/2fa/replace").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void returns_429_after_exceeding_totp_disable_attempts() throws Exception {
        String body = "{\"currentPassword\":\"Pwd@1234\",\"code\":\"000000\"}";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(delete("/auth/2fa").contentType(MediaType.APPLICATION_JSON).content(body));
        }
        mockMvc.perform(delete("/auth/2fa").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * Mesmo risco de força bruta de /auth/verify-email (código de 12 chars) — sem este teste, a
     * ausência de "/auth/confirm-email-change" em LoginRateLimitingFilter.shouldNotFilter passaria
     * despercebida.
     */
    @Test
    void returns_429_after_exceeding_confirm_email_change_attempts() throws Exception {
        String body = "{\"code\":\"invalidcode123\"}";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/confirm-email-change").contentType(MediaType.APPLICATION_JSON).content(body));
        }
        mockMvc.perform(post("/auth/confirm-email-change").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }
}
