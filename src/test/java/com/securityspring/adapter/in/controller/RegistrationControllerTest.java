package com.securityspring.adapter.in.controller;

import com.securityspring.core.domain.exception.EmailAlreadyExistsException;
import com.securityspring.core.domain.exception.EmailVerificationCodeNotFoundException;
import com.securityspring.core.domain.exception.UsernameAlreadyExistsException;
import com.securityspring.core.ports.in.UserUseCase;
import com.securityspring.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa {@link RegistrationController} com MockMvc standalone.
 * Cobre os caminhos de erro que os ITs não exercitam explicitamente.
 */
class RegistrationControllerTest {

    private MockMvc mockMvc;
    private UserUseCase useCase;

    @BeforeEach
    void setup() {
        useCase = mock(UserUseCase.class);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(exceptionHandler, "lockoutDurationMinutes", 15L);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new RegistrationController(useCase, ""))
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    // ── /auth/register ────────────────────────────────────────────────────────

    @Test
    void register_valid_returns_201() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"password\":\"Secure@1\",\"email\":\"user@test.com\"}"))
                .andExpect(status().isCreated());

        verify(useCase).registerUser(eq("newuser"), eq("Secure@1"), eq("user@test.com"), any());
    }

    @Test
    void register_blank_username_returns_400() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"Secure@1\",\"email\":\"u@t.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void register_weak_password_returns_400() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bob\",\"password\":\"fraca\",\"email\":\"b@t.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void register_missing_email_returns_400() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bob\",\"password\":\"Secure@1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicate_username_returns_409() throws Exception {
        doThrow(new UsernameAlreadyExistsException("bob"))
                .when(useCase).registerUser(eq("bob"), any(), any(), any());

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bob\",\"password\":\"Secure@1\",\"email\":\"b@t.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void register_duplicate_email_returns_409() throws Exception {
        doThrow(new EmailAlreadyExistsException("b@t.com"))
                .when(useCase).registerUser(any(), any(), eq("b@t.com"), any());

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bob\",\"password\":\"Secure@1\",\"email\":\"b@t.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_EXISTS"));
    }

    // ── /auth/verify-email ────────────────────────────────────────────────────

    @Test
    void verifyEmail_valid_returns_204() throws Exception {
        mockMvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ABC123\"}"))
                .andExpect(status().isNoContent());

        verify(useCase).verifyEmail("ABC123");
    }

    @Test
    void verifyEmail_invalidCode_returns_400() throws Exception {
        doThrow(new EmailVerificationCodeNotFoundException())
                .when(useCase).verifyEmail("INVALID");

        mockMvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_CODE_INVALID"));
    }

    // ── /auth/resend-verification ─────────────────────────────────────────────

    @Test
    void resendVerification_always_returns_204() throws Exception {
        // Endpoint é opaco: não revela se o email existe (defesa contra enumeração)
        mockMvc.perform(post("/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.com\"}"))
                .andExpect(status().isNoContent());
    }
}
