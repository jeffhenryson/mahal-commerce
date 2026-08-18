package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.support.BugReport;
import com.cernecommerce.core.ports.in.BugReportUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class SupportControllerTest {

    private MockMvc mockMvc;
    private BugReportUseCase bugReportUseCase;
    private ApplicationEventPublisher publisher;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("alice", null, List.of());

    private static final Instant NOW = Instant.parse("2026-06-09T10:00:00Z");

    @BeforeEach
    void setup() {
        bugReportUseCase = mock(BugReportUseCase.class);
        publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SupportController(bugReportUseCase, publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createBugReport_returns_201_and_usesAuthenticatedUserAsReporter() throws Exception {
        when(bugReportUseCase.createBugReport(eq("alice"), eq("Botão quebrado"), eq("Ao clicar nada acontece"),
                eq("/app/pedidos"), eq("Mozilla/5.0")))
                .thenReturn(new BugReport(1L, "alice", "Botão quebrado", "Ao clicar nada acontece",
                        "/app/pedidos", "Mozilla/5.0", NOW));

        mockMvc.perform(post("/support/bug-reports")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Mozilla/5.0")
                        .content("{\"title\":\"Botão quebrado\",\"description\":\"Ao clicar nada acontece\","
                                + "\"pageUrl\":\"/app/pedidos\",\"userAgent\":\"Mozilla/5.0\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        verify(bugReportUseCase).createBugReport("alice", "Botão quebrado", "Ao clicar nada acontece",
                "/app/pedidos", "Mozilla/5.0");
        verify(publisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    void createBugReport_fallsBackToUserAgentHeaderWhenBodyFieldMissing() throws Exception {
        when(bugReportUseCase.createBugReport(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new BugReport(1L, "alice", "Título", "Descrição", null, "header-agent", NOW));

        mockMvc.perform(post("/support/bug-reports")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "header-agent")
                        .content("{\"title\":\"Título\",\"description\":\"Descrição\"}"))
                .andExpect(status().isCreated());

        verify(bugReportUseCase).createBugReport(eq("alice"), eq("Título"), eq("Descrição"), isNull(),
                eq("header-agent"));
    }

    @Test
    void createBugReport_withoutTitle_returns_400() throws Exception {
        mockMvc.perform(post("/support/bug-reports")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Ao clicar nada acontece\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bugReportUseCase);
    }

    @Test
    void createBugReport_withoutDescription_returns_400() throws Exception {
        mockMvc.perform(post("/support/bug-reports")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Botão quebrado\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bugReportUseCase);
    }
}
