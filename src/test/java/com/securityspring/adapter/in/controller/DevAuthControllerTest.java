package com.securityspring.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securityspring.adapter.in.dtos.request.DevCompleteRequest;
import com.securityspring.adapter.in.dtos.request.DevFirstCodeRequest;
import com.securityspring.core.domain.exception.auth.DevChallengeExpiredException;
import com.securityspring.core.domain.exception.auth.InvalidTotpCodeException;
import com.securityspring.core.domain.model.auth.DevElevationResult;
import com.securityspring.core.ports.in.AuthUseCase;
import com.securityspring.core.ports.in.TotpUseCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

public class DevAuthControllerTest {

    private MockMvc mockMvc;
    private TotpUseCase totpUseCase;
    private AuthUseCase authUseCase;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UsernamePasswordAuthenticationToken DEV_AUTH =
            new UsernamePasswordAuthenticationToken("devuser", null, List.of());

    @BeforeEach
    void setup() {
        totpUseCase = mock(TotpUseCase.class);
        authUseCase = mock(AuthUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        DevAuthController controller = new DevAuthController(totpUseCase, authUseCase, publisher);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.securityspring.infra.handler.GlobalExceptionHandler())
                .build();
    }

    // ── Etapa 1: first-code ───────────────────────────────────────────────────

    @Test
    void first_code_returns_dev_token_and_expires_in() throws Exception {
        when(totpUseCase.issueDevFirstCode("devuser", "123456")).thenReturn("dev-token-abc");

        DevFirstCodeRequest req = new DevFirstCodeRequest();
        req.setTotpCode("123456");

        mockMvc.perform(post("/auth/dev/first-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(DEV_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devToken").value("dev-token-abc"))
                .andExpect(jsonPath("$.expiresIn").value(90));
    }

    @Test
    void first_code_with_invalid_totp_returns_400() throws Exception {
        when(totpUseCase.issueDevFirstCode(any(), eq("000000")))
                .thenThrow(new InvalidTotpCodeException());

        DevFirstCodeRequest req = new DevFirstCodeRequest();
        req.setTotpCode("000000");

        mockMvc.perform(post("/auth/dev/first-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(DEV_AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOTP_CODE"));
    }

    @Test
    void first_code_with_blank_totp_returns_400_validation() throws Exception {
        DevFirstCodeRequest req = new DevFirstCodeRequest();
        req.setTotpCode("");

        mockMvc.perform(post("/auth/dev/first-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(DEV_AUTH))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(totpUseCase);
    }

    @Test
    void first_code_with_non_numeric_totp_returns_400_validation() throws Exception {
        DevFirstCodeRequest req = new DevFirstCodeRequest();
        req.setTotpCode("abc123");

        mockMvc.perform(post("/auth/dev/first-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(DEV_AUTH))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(totpUseCase);
    }

    // ── Etapa 2: complete ─────────────────────────────────────────────────────

    @Test
    void complete_returns_dev_access_token() throws Exception {
        when(authUseCase.completeDevElevation("dev-token-xyz", "654321"))
                .thenReturn(new DevElevationResult("devuser", "dev-access-token-elevated"));

        DevCompleteRequest req = new DevCompleteRequest();
        req.setDevToken("dev-token-xyz");
        req.setTotpCode("654321");

        mockMvc.perform(post("/auth/dev/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("dev-access-token-elevated"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void complete_with_expired_dev_token_returns_410() throws Exception {
        when(authUseCase.completeDevElevation(eq("expired-dev-token"), any()))
                .thenThrow(new DevChallengeExpiredException());

        DevCompleteRequest req = new DevCompleteRequest();
        req.setDevToken("expired-dev-token");
        req.setTotpCode("123456");

        mockMvc.perform(post("/auth/dev/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.errorCode").value("DEV_CHALLENGE_EXPIRED"));
    }

    @Test
    void complete_with_invalid_second_totp_returns_400() throws Exception {
        when(authUseCase.completeDevElevation(any(), eq("000000")))
                .thenThrow(new InvalidTotpCodeException());

        DevCompleteRequest req = new DevCompleteRequest();
        req.setDevToken("valid-dev-token");
        req.setTotpCode("000000");

        mockMvc.perform(post("/auth/dev/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOTP_CODE"));
    }

    @Test
    void complete_with_blank_dev_token_returns_400_validation() throws Exception {
        DevCompleteRequest req = new DevCompleteRequest();
        req.setDevToken("");
        req.setTotpCode("123456");

        mockMvc.perform(post("/auth/dev/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authUseCase);
    }
}
