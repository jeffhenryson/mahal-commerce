package com.securityspring.infra.security;

import com.securityspring.core.ports.out.ratelimit.LoginRateLimiterPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa {@link LoginRateLimitingFilter} via MockMvc standalone com um controller mínimo,
 * verificando quais paths são sujeitos ao rate limit e o comportamento do 429.
 */
class LoginRateLimitingFilterTest {

    /** Controller mínimo para dar rotas ao MockMvc. */
    @RestController
    static class StubController {
        @PostMapping("/auth/login")            public void login()          {}
        @PostMapping("/auth/register")         public void register()       {}
        @PostMapping("/auth/verify-email")     public void verifyEmail()    {}
        @PostMapping("/auth/resend-verification") public void resend()      {}
        @PostMapping("/auth/refresh")          public void refresh()        {}
        @PostMapping("/auth/logout")           public void logout()         {}
        @PostMapping("/auth/2fa/verify")       public void totpVerify()     {}
        @PostMapping("/auth/2fa/confirm")      public void totpConfirm()    {}
        @PostMapping("/auth/oauth2/google")    public void oauthGoogle()    {}
        @PostMapping("/auth/dev/first-code")   public void devFirstCode()   {}
        @PostMapping("/auth/dev/complete")     public void devComplete()    {}
        @GetMapping("/users/me")               public void me()             {}
        @GetMapping("/notifications/stream")   public void sseStream()      {}
        @PutMapping("/notifications/preferences/PASSWORD_CHANGED") public void updatePref() {}
        @PutMapping("/users/me")               public void updateMe()       {}
        @DeleteMapping("/auth/2fa")            public void disable2fa()     {}
    }

    private LoginRateLimiterPort rateLimiter;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(LoginRateLimiterPort.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StubController())
                .addFilters(new LoginRateLimitingFilter(rateLimiter, new SimpleMeterRegistry(), 60L))
                .build();
    }

    // ── paths que NÃO são rate-limitados ────────────────────────────────────

    @Test
    void get_request_bypasses_filter() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk());
        verifyNoInteractions(rateLimiter);
    }

    // ── GET /notifications/stream rate-limiting ───────────────────────────────

    @Test
    void get_to_sse_stream_within_limit_passes_through() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(true);

        mockMvc.perform(get("/notifications/stream"))
                .andExpect(status().isOk());
        verify(rateLimiter).tryConsume(any());
    }

    @Test
    void get_to_sse_stream_exceeded_returns_429() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(false);

        mockMvc.perform(get("/notifications/stream"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void post_to_non_auth_path_bypasses_filter() throws Exception {
        mockMvc.perform(post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verifyNoInteractions(rateLimiter);
    }

    // ── paths rate-limitados: dentro do limite ───────────────────────────────

    @Test
    void login_within_limit_passes_through() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(true);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(rateLimiter).tryConsume(any());
    }

    @Test
    void register_within_limit_passes_through() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(true);

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(rateLimiter).tryConsume(any());
    }

    @Test
    void verify_email_within_limit_passes_through() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(true);

        mockMvc.perform(post("/auth/verify-email").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(rateLimiter).tryConsume(any());
    }

    @Test
    void refresh_within_limit_passes_through() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(true);

        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(rateLimiter).tryConsume(any());
    }

    // ── paths rate-limitados: limite excedido ────────────────────────────────

    @Test
    void login_exceeded_returns_429_with_retry_after_and_json_body() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(false);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void register_exceeded_returns_429() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(false);

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());
    }

    // ── /auth/2fa/confirm rate-limiting ─────────────────────────────────────

    @Test
    void totp_confirm_within_limit_passes_through() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(true);

        mockMvc.perform(post("/auth/2fa/confirm").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(rateLimiter).tryConsume(any());
    }

    @Test
    void totp_confirm_exceeded_returns_429() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(false);

        mockMvc.perform(post("/auth/2fa/confirm").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));
    }

    // ── /auth/oauth2/google rate-limiting ────────────────────────────────────

    @Test
    void oauth_google_within_limit_passes_through() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(true);

        mockMvc.perform(post("/auth/oauth2/google").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(rateLimiter).tryConsume(any());
    }

    @Test
    void oauth_google_exceeded_returns_429() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(false);

        mockMvc.perform(post("/auth/oauth2/google").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));
    }

    // ── /auth/dev endpoints rate-limiting ────────────────────────────────────

    @Test
    void dev_first_code_within_limit_passes_through() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(true);

        mockMvc.perform(post("/auth/dev/first-code").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(rateLimiter).tryConsume(any());
    }

    @Test
    void dev_first_code_exceeded_returns_429() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(false);

        mockMvc.perform(post("/auth/dev/first-code").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void dev_complete_within_limit_passes_through() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(true);

        mockMvc.perform(post("/auth/dev/complete").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(rateLimiter).tryConsume(any());
    }

    @Test
    void dev_complete_exceeded_returns_429() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(false);

        mockMvc.perform(post("/auth/dev/complete").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));
    }

    // ── /notifications/preferences PUT rate-limiting ─────────────────────────

    @Test
    void put_to_preferences_within_limit_passes_through() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(true);

        mockMvc.perform(put("/notifications/preferences/PASSWORD_CHANGED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(rateLimiter).tryConsume(any());
    }

    @Test
    void put_to_preferences_exceeded_returns_429() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(false);

        mockMvc.perform(put("/notifications/preferences/PASSWORD_CHANGED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void put_to_non_preferences_path_bypasses_filter() throws Exception {
        mockMvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verifyNoInteractions(rateLimiter);
    }
}
