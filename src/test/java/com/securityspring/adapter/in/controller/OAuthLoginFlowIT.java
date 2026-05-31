package com.securityspring.adapter.in.controller;

import com.securityspring.core.domain.exception.auth.OAuthTokenInvalidException;
import com.securityspring.core.domain.model.auth.GoogleUserInfo;
import com.securityspring.core.ports.out.oauth.GoogleTokenVerifierPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("dev")
class OAuthLoginFlowIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private GoogleTokenVerifierPort tokenVerifier;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ── happy paths ───────────────────────────────────────────────────────────

    @Test
    void loginWithGoogle_newUser_returns200_withAccessToken() throws Exception {
        when(tokenVerifier.verify("valid-token"))
                .thenReturn(new GoogleUserInfo("google-it-001", "newuser@example.com", "New User"));

        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"valid-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    void loginWithGoogle_normalizes_email_to_lowercase() throws Exception {
        // Email com uppercase — deve criar/encontrar usuário com email em lowercase
        when(tokenVerifier.verify("uppercase-token"))
                .thenReturn(new GoogleUserInfo("google-it-002", "UpperCase@Example.COM", "Upper User"));

        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"uppercase-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Segunda chamada com mesmo email em case diferente deve retornar o mesmo usuário (via googleId link)
        when(tokenVerifier.verify("uppercase-token-2"))
                .thenReturn(new GoogleUserInfo("google-it-002", "uppercase@example.com", "Upper User"));

        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"uppercase-token-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void loginWithGoogle_secondLogin_sameUser_succeeds() throws Exception {
        when(tokenVerifier.verify("repeat-token"))
                .thenReturn(new GoogleUserInfo("google-it-003", "repeat@example.com", "Repeat User"));

        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"repeat-token\"}"))
                .andExpect(status().isOk());

        // Segundo login com o mesmo Google ID deve funcionar
        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"repeat-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    // ── error paths ───────────────────────────────────────────────────────────

    @Test
    void loginWithGoogle_invalidToken_returns401() throws Exception {
        when(tokenVerifier.verify("bad-token"))
                .thenThrow(new OAuthTokenInvalidException("Token Google inválido ou expirado"));

        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("OAUTH_TOKEN_INVALID"));
    }

    @Test
    void loginWithGoogle_missingIdToken_returns400() throws Exception {
        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
