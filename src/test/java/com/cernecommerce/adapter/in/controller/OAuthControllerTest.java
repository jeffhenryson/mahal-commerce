package com.cernecommerce.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cernecommerce.adapter.in.dtos.request.GoogleLoginRequest;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.auth.OAuthTokenInvalidException;
import com.cernecommerce.core.domain.model.auth.OAuthLoginResult;
import com.cernecommerce.core.domain.model.auth.TokenPair;
import com.cernecommerce.core.ports.in.OAuthLoginUseCase;
import com.cernecommerce.core.ports.in.SystemConfigUseCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class OAuthControllerTest {

    private MockMvc mockMvc;
    private OAuthLoginUseCase oAuthLoginUseCase;
    private SystemConfigUseCase systemConfig;
    private ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        oAuthLoginUseCase = mock(OAuthLoginUseCase.class);
        systemConfig = mock(SystemConfigUseCase.class);
        publisher = mock(ApplicationEventPublisher.class);

        when(systemConfig.getBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));

        OAuthController controller = new OAuthController(oAuthLoginUseCase, publisher, systemConfig, 15, 7L, false);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.cernecommerce.infra.handler.GlobalExceptionHandler())
                .build();
    }

    @Test
    void google_login_returns_access_and_refresh_token() throws Exception {
        when(oAuthLoginUseCase.loginWithGoogle("valid-google-token"))
                .thenReturn(new OAuthLoginResult(new TokenPair("access123", "refresh456"), "alice"));

        mockMvc.perform(post("/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("valid-google-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access123"))
                .andExpect(jsonPath("$.refreshToken").value("refresh456"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void google_login_sets_httponly_refresh_cookie() throws Exception {
        when(oAuthLoginUseCase.loginWithGoogle("valid-google-token"))
                .thenReturn(new OAuthLoginResult(new TokenPair("access123", "refresh456"), "alice"));

        mockMvc.perform(post("/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("valid-google-token"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refreshToken=refresh456")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")));
    }

    @Test
    void google_login_with_blank_token_returns_400() throws Exception {
        mockMvc.perform(post("/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(oAuthLoginUseCase);
    }

    @Test
    void google_login_with_missing_body_returns_400() throws Exception {
        mockMvc.perform(post("/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(oAuthLoginUseCase);
    }

    @Test
    void google_login_with_invalid_token_returns_401() throws Exception {
        when(oAuthLoginUseCase.loginWithGoogle("expired-token"))
                .thenThrow(new OAuthTokenInvalidException("Token Google inválido ou expirado"));

        mockMvc.perform(post("/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("expired-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("OAUTH_TOKEN_INVALID"));
    }

    @Test
    void google_login_returns_503_when_oauth_disabled() throws Exception {
        when(systemConfig.getBoolean("auth.google.enabled", true)).thenReturn(false);

        mockMvc.perform(post("/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("any-token"))))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(oAuthLoginUseCase);
    }

    @Test
    void google_login_publishes_audit_event_when_disabled() throws Exception {
        when(systemConfig.getBoolean("auth.google.enabled", true)).thenReturn(false);

        mockMvc.perform(post("/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("any-token"))))
                .andExpect(status().isServiceUnavailable());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(eventCaptor.capture());
        Object capturedEvent = eventCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(capturedEvent).isInstanceOf(AuditEvent.class);
        org.assertj.core.api.Assertions.assertThat(((AuditEvent) capturedEvent).type())
            .isEqualTo(AuditEvent.EventType.OAUTH_GOOGLE_DISABLED_ATTEMPT);
    }
}
