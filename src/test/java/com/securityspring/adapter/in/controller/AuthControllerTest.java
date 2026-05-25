package com.securityspring.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securityspring.adapter.in.dtos.request.LoginRequest;
import com.securityspring.adapter.in.dtos.request.LogoutRequest;
import com.securityspring.adapter.in.dtos.request.RefreshRequest;
import com.securityspring.core.domain.model.TokenPair;
import com.securityspring.core.ports.in.AuthUseCase;
import com.securityspring.core.ports.in.UserUseCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthUseCase authUseCase;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        authUseCase = mock(AuthUseCase.class);
        UserUseCase userUseCase = mock(UserUseCase.class);
        AuthController controller = new AuthController(authUseCase, userUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.securityspring.infra.handler.GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_returns_access_and_refresh() throws Exception {
        var req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Admin@dev1");

        when(authUseCase.login("admin", "Admin@dev1"))
                .thenReturn(new TokenPair("access123", "refresh123"));

        mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access123"))
                .andExpect(jsonPath("$.refreshToken").value("refresh123"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_with_bad_credentials_returns_401() throws Exception {
        var req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrong");

        when(authUseCase.login(any(), any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void refresh_with_invalid_token_returns_400() throws Exception {
        var req = new RefreshRequest();
        req.setRefreshToken("invalid");

        when(authUseCase.refresh("invalid"))
                .thenThrow(new IllegalArgumentException("Invalid refresh token"));

        mockMvc.perform(post("/auth/refresh")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_rotates_and_returns_new_pair() throws Exception {
        var req = new RefreshRequest();
        req.setRefreshToken("oldRefresh");

        when(authUseCase.refresh("oldRefresh"))
                .thenReturn(new TokenPair("newAccess", "newRefresh"));

        mockMvc.perform(post("/auth/refresh")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("newAccess"))
                .andExpect(jsonPath("$.refreshToken").value("newRefresh"));
    }

    @Test
    void logout_revokes_refresh() throws Exception {
        var req = new LogoutRequest();
        req.setRefreshToken("ref");

        mockMvc.perform(post("/auth/logout")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(authUseCase, times(1)).logout("ref");
    }
}
