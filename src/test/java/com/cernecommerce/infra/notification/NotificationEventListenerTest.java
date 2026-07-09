package com.cernecommerce.infra.notification;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.NotificationPreferenceUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class NotificationEventListenerTest {

    @Mock NotificationUseCase notificationUseCase;
    @Mock NotificationPreferenceUseCase preferenceUseCase;
    @Mock UserRepository userRepository;
    @Mock EmailPort emailPort;
    @Mock NotificationSsePort ssePort;

    NotificationEventListener listener;

    private static final Notification SAVED = new Notification(
            1L, "alice", NotificationType.PASSWORD_CHANGED, "Senha alterada", "...", null, Instant.now());

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new NotificationEventListener(notificationUseCase, preferenceUseCase,
                userRepository, emailPort, ssePort);
    }

    @Test
    void password_changed_persiste_envia_sse_e_email() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(todosHabilitados("alice"));
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_PASSWORD_CHANGED, "alice"));

        verify(notificationUseCase).notify(eq("alice"), eq(NotificationType.PASSWORD_CHANGED), any(), any());
        verify(ssePort).send(eq("alice"), eq(SAVED));
        verify(emailPort).sendPasswordChangedAlert("alice@example.com", "alice");
    }

    @Test
    void inapp_desabilitado_pula_persistencia_e_sse_mas_envia_email() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(
                List.of(new NotificationPreference("alice", NotificationType.PASSWORD_CHANGED, false, true)));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_PASSWORD_CHANGED, "alice"));

        verify(notificationUseCase, never()).notify(any(), any(), any(), any());
        verify(ssePort, never()).send(any(), any());
        verify(emailPort).sendPasswordChangedAlert(eq("alice@example.com"), eq("alice"));
    }

    @Test
    void email_desabilitado_persiste_e_envia_sse_mas_pula_email() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(
                List.of(new NotificationPreference("alice", NotificationType.PASSWORD_CHANGED, true, false)));
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_PASSWORD_CHANGED, "alice"));

        verify(notificationUseCase).notify(eq("alice"), eq(NotificationType.PASSWORD_CHANGED), any(), any());
        verify(ssePort).send(eq("alice"), eq(SAVED));
        verify(emailPort, never()).sendPasswordChangedAlert(any(), any());
    }

    @Test
    void falha_na_lookup_de_preferencia_usa_defaults_todos_habilitados() {
        given(preferenceUseCase.getPreferences("alice")).willThrow(new RuntimeException("db error"));
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_PASSWORD_CHANGED, "alice"));

        verify(notificationUseCase).notify(eq("alice"), eq(NotificationType.PASSWORD_CHANGED), any(), any());
        verify(emailPort).sendPasswordChangedAlert(any(), any());
    }

    @Test
    void role_assigned_inclui_nome_do_role_no_corpo() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(
                List.of(new NotificationPreference("alice", NotificationType.ROLE_ASSIGNED, true, false)));
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_ROLE_ASSIGNED, "alice",
                Map.of("role", "ROLE_ADMIN")));

        verify(notificationUseCase).notify(
                eq("alice"), eq(NotificationType.ROLE_ASSIGNED), any(), contains("ROLE_ADMIN"));
    }

    @Test
    void tipo_de_evento_nao_mapeado_e_ignorado() {
        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_LOGGED_IN, "alice"));

        verifyNoInteractions(notificationUseCase, ssePort, emailPort);
    }

    @Test
    void account_locked_persiste_e_envia_email() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(todosHabilitados("alice"));
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ACCOUNT_LOCKED, "alice"));

        verify(notificationUseCase).notify(eq("alice"), eq(NotificationType.ACCOUNT_LOCKED), any(), any());
        verify(emailPort).sendAccountLockedAlert(eq("alice@example.com"), eq("alice"));
    }

    // helpers

    private List<NotificationPreference> todosHabilitados(String username) {
        return List.of(
                new NotificationPreference(username, NotificationType.PASSWORD_CHANGED, true, true),
                new NotificationPreference(username, NotificationType.ACCOUNT_LOCKED, true, true),
                new NotificationPreference(username, NotificationType.ROLE_ASSIGNED, true, true));
    }

    private User stubUser() {
        return User.ofPendingVerification("alice", "hash", "alice@example.com", Set.of());
    }
}
