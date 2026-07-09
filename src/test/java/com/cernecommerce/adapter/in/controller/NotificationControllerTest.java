package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.sse.SseEmitterRegistry;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class NotificationControllerTest {

    private MockMvc mockMvc;
    private NotificationUseCase useCase;
    private SseEmitterRegistry sseRegistry;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("alice", null, List.of());

    private static final Instant NOW = Instant.parse("2026-06-09T10:00:00Z");

    @BeforeEach
    void setup() {
        useCase = mock(NotificationUseCase.class);
        sseRegistry = mock(SseEmitterRegistry.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(useCase, sseRegistry))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Notification notification(Long id, NotificationType type) {
        return new Notification(id, "alice", type, "Título", "Corpo", null, NOW);
    }

    // ── GET /notifications ────────────────────────────────────────────────────

    @Test
    void list_returns_200_with_paged_notifications() throws Exception {
        when(useCase.getNotifications(eq("alice"), eq(false), eq(0), eq(20)))
                .thenReturn(new PageResult<>(
                        List.of(notification(1L, NotificationType.PASSWORD_CHANGED)),
                        0, 20, 1L, 1));

        mockMvc.perform(get("/notifications").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].type").value("PASSWORD_CHANGED"))
                .andExpect(jsonPath("$.content[0].title").value("Título"))
                .andExpect(jsonPath("$.content[0].body").value("Corpo"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_with_unread_only_passes_flag_to_use_case() throws Exception {
        when(useCase.getNotifications(eq("alice"), eq(true), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/notifications").param("unreadOnly", "true").principal(AUTH))
                .andExpect(status().isOk());

        verify(useCase).getNotifications("alice", true, 0, 20);
    }

    @Test
    void list_with_pagination_passes_params_to_use_case() throws Exception {
        when(useCase.getNotifications(eq("alice"), eq(false), eq(2), eq(10)))
                .thenReturn(new PageResult<>(List.of(), 2, 10, 0L, 0));

        mockMvc.perform(get("/notifications")
                        .param("page", "2").param("size", "10")
                        .principal(AUTH))
                .andExpect(status().isOk());

        verify(useCase).getNotifications("alice", false, 2, 10);
    }

    @Test
    void list_read_notification_serializes_read_true() throws Exception {
        Notification read = new Notification(2L, "alice", NotificationType.ACCOUNT_LOCKED,
                "Conta bloqueada", "Detalhe", NOW, NOW);
        when(useCase.getNotifications(eq("alice"), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(read), 0, 20, 1L, 1));

        mockMvc.perform(get("/notifications").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].read").value(true));
    }

    // ── GET /notifications/unread-count ───────────────────────────────────────

    @Test
    void unreadCount_returns_200_with_count() throws Exception {
        when(useCase.countUnread("alice")).thenReturn(5L);

        mockMvc.perform(get("/notifications/unread-count").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    void unreadCount_returns_zero_when_none() throws Exception {
        when(useCase.countUnread("alice")).thenReturn(0L);

        mockMvc.perform(get("/notifications/unread-count").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    // ── PATCH /notifications/{id}/read ────────────────────────────────────────

    @Test
    void markAsRead_returns_204_and_calls_use_case() throws Exception {
        mockMvc.perform(patch("/notifications/42/read").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(useCase).markAsRead("alice", 42L);
    }

    // ── PATCH /notifications/read-all ─────────────────────────────────────────

    @Test
    void markAllAsRead_returns_204_and_calls_use_case() throws Exception {
        mockMvc.perform(patch("/notifications/read-all").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(useCase).markAllAsRead("alice");
    }

    // ── DELETE /notifications/{id} ────────────────────────────────────────────

    @Test
    void delete_returns_204_and_calls_use_case() throws Exception {
        mockMvc.perform(delete("/notifications/7").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(useCase).delete("alice", 7L);
    }

    // ── GET /notifications/stream ─────────────────────────────────────────────

    @Test
    void stream_registers_emitter_for_authenticated_user() throws Exception {
        mockMvc.perform(get("/notifications/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE)
                        .principal(AUTH))
                .andReturn();

        verify(sseRegistry).register(eq("alice"), any(SseEmitter.class));
    }
}
