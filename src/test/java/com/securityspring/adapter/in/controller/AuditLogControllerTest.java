package com.securityspring.adapter.in.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.ports.in.AuditLogsUseCase;
import com.securityspring.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

public class AuditLogControllerTest {

    private MockMvc mockMvc;
    private AuditLogsUseCase useCase;

    private static final Instant TS = Instant.parse("2026-05-29T10:00:00Z");

    @BeforeEach
    void setup() {
        useCase = mock(AuditLogsUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuditLogController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AuditLogEntry entry(Long id, String who, String action) {
        return new AuditLogEntry(id, who, action, null, "{\"role\":\"ROLE_ADMIN\"}", "127.0.0.1", TS);
    }

    @Test
    void list_returns_200_with_paged_entries() throws Exception {
        when(useCase.list(isNull(), isNull(), isNull(), isNull(), eq(0), eq(20), any()))
                .thenReturn(new PageResult<>(List.of(entry(1L, "alice", "USER_CREATED")), 0, 20, 1L, 1));

        mockMvc.perform(get("/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].who").value("alice"))
                .andExpect(jsonPath("$.content[0].action").value("USER_CREATED"))
                .andExpect(jsonPath("$.content[0].details").value("{\"role\":\"ROLE_ADMIN\"}"))
                .andExpect(jsonPath("$.content[0].ipAddress").value("127.0.0.1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_with_username_filter_passes_to_use_case() throws Exception {
        when(useCase.list(eq("alice"), isNull(), isNull(), isNull(), eq(0), eq(20), any()))
                .thenReturn(new PageResult<>(List.of(entry(1L, "alice", "USER_LOGGED_IN")), 0, 20, 1L, 1));

        mockMvc.perform(get("/audit-logs").param("username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].who").value("alice"));

        verify(useCase).list(eq("alice"), isNull(), isNull(), isNull(), eq(0), eq(20), any());
    }

    @Test
    void list_with_action_filter_passes_to_use_case() throws Exception {
        when(useCase.list(isNull(), eq("USER_DELETED"), isNull(), isNull(), eq(0), eq(20), any()))
                .thenReturn(new PageResult<>(List.of(entry(2L, "admin", "USER_DELETED")), 0, 20, 1L, 1));

        mockMvc.perform(get("/audit-logs").param("action", "USER_DELETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("USER_DELETED"));

        verify(useCase).list(isNull(), eq("USER_DELETED"), isNull(), isNull(), eq(0), eq(20), any());
    }

    @Test
    void list_with_pagination_params_passes_to_use_case() throws Exception {
        when(useCase.list(isNull(), isNull(), isNull(), isNull(), eq(2), eq(10), any()))
                .thenReturn(new PageResult<>(List.of(), 2, 10, 0L, 0));

        mockMvc.perform(get("/audit-logs").param("page", "2").param("size", "10"))
                .andExpect(status().isOk());

        verify(useCase).list(isNull(), isNull(), isNull(), isNull(), eq(2), eq(10), any());
    }

    @Test
    void list_caps_size_at_100() throws Exception {
        when(useCase.list(isNull(), isNull(), isNull(), isNull(), eq(0), eq(100), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 100, 0L, 0));

        mockMvc.perform(get("/audit-logs").param("size", "200"))
                .andExpect(status().isOk());

        verify(useCase).list(isNull(), isNull(), isNull(), isNull(), eq(0), eq(100), any());
    }

    @Test
    void list_with_date_range_passes_to_use_case() throws Exception {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-31T23:59:59Z");
        when(useCase.list(isNull(), isNull(), eq(from), eq(to), eq(0), eq(20), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/audit-logs")
                        .param("from", "2026-05-01T00:00:00Z")
                        .param("to", "2026-05-31T23:59:59Z"))
                .andExpect(status().isOk());

        verify(useCase).list(isNull(), isNull(), eq(from), eq(to), eq(0), eq(20), any());
    }

    @Test
    void list_response_serializes_timestamp_as_iso_string() throws Exception {
        when(useCase.list(isNull(), isNull(), isNull(), isNull(), eq(0), eq(20), any()))
                .thenReturn(new PageResult<>(List.of(entry(1L, "alice", "USER_LOGGED_IN")), 0, 20, 1L, 1));

        mockMvc.perform(get("/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].timestamp").value("2026-05-29T10:00:00Z"));
    }

    // ── listActions ───────────────────────────────────────────────────────────

    @Test
    void listActions_returns_200_with_sorted_list_of_event_types() throws Exception {
        mockMvc.perform(get("/audit-logs/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").isString());
    }

    @Test
    void listActions_contains_known_event_types() throws Exception {
        mockMvc.perform(get("/audit-logs/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@ == 'USER_LOGGED_IN')]").exists())
                .andExpect(jsonPath("$[?(@ == 'USER_REGISTERED')]").exists())
                .andExpect(jsonPath("$[?(@ == 'TOTP_ENABLED')]").exists())
                .andExpect(jsonPath("$[?(@ == 'TOKEN_THEFT_DETECTED')]").exists());
    }

    @Test
    void listActions_is_sorted_alphabetically() throws Exception {
        String body = mockMvc.perform(get("/audit-logs/actions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        List<String> actions = om.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        List<String> sorted = actions.stream().sorted().toList();
        org.assertj.core.api.Assertions.assertThat(actions).isEqualTo(sorted);
    }
}
