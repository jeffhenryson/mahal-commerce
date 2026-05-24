package com.security_spring.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.security_spring.adapter.in.dtos.LoginRequest;
import com.security_spring.adapter.in.dtos.LogoutRequest;
import com.security_spring.adapter.in.dtos.RefreshRequest;
import com.security_spring.infra.security.JwtService;
import com.security_spring.infra.security.RefreshTokenService;
import com.security_spring.infra.security.RefreshTokenService.RotationResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

public class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthenticationManager authenticationManager;
    private JwtService jwtService;
    private RefreshTokenService refreshService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        authenticationManager = mock(AuthenticationManager.class);
        jwtService = mock(JwtService.class);
        refreshService = mock(RefreshTokenService.class);

        AuthController controller = new AuthController(authenticationManager, jwtService, refreshService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void login_returns_access_and_refresh() throws Exception {
        var req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin");

        User principal = new User("admin", "pwd", Collections.emptyList());
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtService.generateAccessToken(any())).thenReturn("access123");
        when(refreshService.issueNewRefreshToken("admin")).thenReturn("refresh123");

        mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access123"))
                .andExpect(jsonPath("$.refreshToken").value("refresh123"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void refresh_rotates_and_returns_new_pair() throws Exception {
        var req = new RefreshRequest();
        req.setRefreshToken("oldRefresh");

        when(refreshService.rotateAndGetUsername("oldRefresh")).thenReturn(new RotationResult("john", "newRefresh"));
        when(jwtService.generateAccessToken(any())).thenReturn("newAccess");

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

        verify(refreshService, times(1)).revoke("ref");
    }
}
