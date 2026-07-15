package com.cernecommerce.adapter.in.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cernecommerce.adapter.in.dtos.request.UpdateNotificationPreferenceRequest;
import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.NotificationPreferenceUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class NotificationPreferenceControllerTest {

    private MockMvc mockMvc;
    private NotificationPreferenceUseCase useCase;
    private final ObjectMapper om = new ObjectMapper();

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("alice", null, List.of());

    @BeforeEach
    void setup() {
        useCase = mock(NotificationPreferenceUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationPreferenceController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private NotificationPreference pref(NotificationType type, boolean inApp, boolean email) {
        return new NotificationPreference("alice", type, inApp, email);
    }

    // ── GET /notifications/preferences ───────────────────────────────────────

    @Test
    void getPreferences_returns_200_with_all_preferences() throws Exception {
        when(useCase.getPreferences("alice")).thenReturn(List.of(
                pref(NotificationType.PASSWORD_CHANGED, true, true),
                pref(NotificationType.ACCOUNT_LOCKED, true, false)));

        mockMvc.perform(get("/notifications/preferences").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].type").value("PASSWORD_CHANGED"))
                .andExpect(jsonPath("$[0].inAppEnabled").value(true))
                .andExpect(jsonPath("$[0].emailEnabled").value(true))
                .andExpect(jsonPath("$[1].type").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$[1].emailEnabled").value(false));
    }

    @Test
    void getPreferences_returns_empty_list_when_no_preferences() throws Exception {
        when(useCase.getPreferences("alice")).thenReturn(List.of());

        mockMvc.perform(get("/notifications/preferences").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── PUT /notifications/preferences/{type} ─────────────────────────────────

    @Test
    void updatePreference_returns_200_with_updated_preference() throws Exception {
        var body = new UpdateNotificationPreferenceRequest(false, true);

        mockMvc.perform(put("/notifications/preferences/PASSWORD_CHANGED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body))
                        .principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PASSWORD_CHANGED"))
                .andExpect(jsonPath("$.inAppEnabled").value(false))
                .andExpect(jsonPath("$.emailEnabled").value(true));

        verify(useCase).updatePreference(eq("alice"), eq(NotificationType.PASSWORD_CHANGED), eq(false), eq(true));
    }

    @Test
    void updatePreference_calls_use_case_with_correct_username() throws Exception {
        var body = new UpdateNotificationPreferenceRequest(true, true);

        mockMvc.perform(put("/notifications/preferences/ACCOUNT_LOCKED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body))
                        .principal(AUTH))
                .andExpect(status().isOk());

        verify(useCase).updatePreference("alice", NotificationType.ACCOUNT_LOCKED, true, true);
    }

    @Test
    void updatePreference_invalid_type_returns_400_with_enum_values() throws Exception {
        var body = new UpdateNotificationPreferenceRequest(true, true);

        mockMvc.perform(put("/notifications/preferences/INVALID_TYPE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body))
                        .principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ENUM_VALUE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("PASSWORD_CHANGED")));

        verifyNoInteractions(useCase);
    }
}
