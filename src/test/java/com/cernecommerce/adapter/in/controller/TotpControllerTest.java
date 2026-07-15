package com.cernecommerce.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.core.domain.exception.auth.TotpAlreadyEnabledException;
import com.cernecommerce.core.domain.exception.auth.TotpNotEnabledException;
import com.cernecommerce.core.ports.in.TotpUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

public class TotpControllerTest {

    private MockMvc mockMvc;
    private TotpUseCase totpUseCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("alice", null, List.of());

    @BeforeEach
    void setup() {
        totpUseCase = mock(TotpUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TotpController(totpUseCase, publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── status ────────────────────────────────────────────────────────────────

    @Test
    void status_returns_enabled_true_when_2fa_is_active() throws Exception {
        when(totpUseCase.isEnabled("alice")).thenReturn(true);

        mockMvc.perform(get("/auth/2fa/status").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void status_returns_enabled_false_when_2fa_is_inactive() throws Exception {
        when(totpUseCase.isEnabled("alice")).thenReturn(false);

        mockMvc.perform(get("/auth/2fa/status").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    // ── setup ─────────────────────────────────────────────────────────────────

    @Test
    void setup_returns_200_with_secret_and_uri() throws Exception {
        when(totpUseCase.setup("alice"))
                .thenReturn(new TotpUseCase.TotpSetupResult("BASE32SECRET", "otpauth://totp/alice"));

        mockMvc.perform(post("/auth/2fa/setup").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("BASE32SECRET"))
                .andExpect(jsonPath("$.otpauthUri").value("otpauth://totp/alice"));
    }

    @Test
    void setup_when_already_enabled_returns_409() throws Exception {
        when(totpUseCase.setup("alice")).thenThrow(new TotpAlreadyEnabledException());

        mockMvc.perform(post("/auth/2fa/setup").principal(AUTH))
                .andExpect(status().isConflict());
    }

    // ── confirm ───────────────────────────────────────────────────────────────

    @Test
    void confirm_returns_200_with_backup_codes() throws Exception {
        when(totpUseCase.confirm("alice", "123456"))
                .thenReturn(List.of("BACKUP1", "BACKUP2", "BACKUP3"));

        mockMvc.perform(post("/auth/2fa/confirm")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupCodes").isArray())
                .andExpect(jsonPath("$.backupCodes[0]").value("BACKUP1"));
    }

    @Test
    void confirm_invalid_code_format_returns_400() throws Exception {
        mockMvc.perform(post("/auth/2fa/confirm")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"abc\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(totpUseCase);
    }

    // ── disable ───────────────────────────────────────────────────────────────

    @Test
    void disable_returns_204() throws Exception {
        mockMvc.perform(delete("/auth/2fa")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Secret@1\",\"code\":\"123456\"}"))
                .andExpect(status().isNoContent());

        verify(totpUseCase).disable("alice", "Secret@1", "123456");
    }

    @Test
    void disable_when_not_enabled_returns_400() throws Exception {
        doThrow(new TotpNotEnabledException())
                .when(totpUseCase).disable(eq("alice"), any(), any());

        mockMvc.perform(delete("/auth/2fa")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Secret@1\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disable_missing_fields_returns_400() throws Exception {
        mockMvc.perform(delete("/auth/2fa")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(totpUseCase);
    }

    // ── regenerate backup codes ───────────────────────────────────────────────

    @Test
    void regenerate_backup_codes_returns_200_with_new_codes() throws Exception {
        when(totpUseCase.regenerateBackupCodes("alice", "Secret@1"))
                .thenReturn(List.of("NEW1", "NEW2", "NEW3"));

        mockMvc.perform(post("/auth/2fa/backup-codes/regenerate")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Secret@1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupCodes[0]").value("NEW1"));
    }

    @Test
    void regenerate_backup_codes_when_not_enabled_returns_400() throws Exception {
        when(totpUseCase.regenerateBackupCodes("alice", "Secret@1"))
                .thenThrow(new TotpNotEnabledException());

        mockMvc.perform(post("/auth/2fa/backup-codes/regenerate")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Secret@1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void regenerate_backup_codes_missing_password_returns_400() throws Exception {
        mockMvc.perform(post("/auth/2fa/backup-codes/regenerate")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(totpUseCase);
    }
}
