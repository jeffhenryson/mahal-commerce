package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.exception.email.EmailVerificationCodeNotFoundException;
import com.cernecommerce.core.domain.exception.user.EmailAlreadyExistsException;
import com.cernecommerce.core.domain.exception.user.UsernameAlreadyExistsException;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.ports.in.SystemConfigUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashSet;

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
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SystemConfigUseCase systemConfig = mock(SystemConfigUseCase.class);
        when(systemConfig.getBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(exceptionHandler, "lockoutDurationMinutes", 15L);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new RegistrationController(useCase, publisher, systemConfig, ""))
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    private User user(String username) {
        return User.fromPersisted(1L, username, "hashed", false, "u@t.com", false, null, null, null, new HashSet<>(), null, null);
    }

    // ── /auth/register ────────────────────────────────────────────────────────

    @Test
    void register_valid_returns_201() throws Exception {
        when(useCase.registerUser(eq("newuser"), eq("Secure@1"), eq("user@test.com"), any()))
                .thenReturn(user("newuser"));

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
        when(useCase.verifyEmail("ABCDEF123456")).thenReturn("newuser");

        mockMvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ABCDEF123456\"}"))
                .andExpect(status().isNoContent());

        verify(useCase).verifyEmail("ABCDEF123456");
    }

    @Test
    void verifyEmail_invalidCode_returns_400() throws Exception {
        doThrow(new EmailVerificationCodeNotFoundException())
                .when(useCase).verifyEmail("INVALID12345");

        mockMvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"INVALID12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_CODE_INVALID"));
    }

    // ── /auth/resend-verification ─────────────────────────────────────────────

    @Test
    void resendVerification_always_returns_204() throws Exception {
        mockMvc.perform(post("/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.com\"}"))
                .andExpect(status().isNoContent());
    }
}
