package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.out.notification.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    NotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    NotificationPreferenceService service;

    @Test
    void getPreferences_retorna_todos_os_tipos_com_defaults_quando_nenhum_armazenado() {
        when(preferenceRepository.findByUsername("alice")).thenReturn(Map.of());

        List<NotificationPreference> prefs = service.getPreferences("alice");

        assertThat(prefs).hasSize(NotificationType.values().length);
        assertThat(prefs).allMatch(NotificationPreference::inAppEnabled);
        assertThat(prefs).allMatch(NotificationPreference::emailEnabled);
    }

    @Test
    void getPreferences_retorna_preferencia_armazenada_quando_existente() {
        NotificationPreference stored = new NotificationPreference("alice", NotificationType.PASSWORD_CHANGED, false, true);
        when(preferenceRepository.findByUsername("alice"))
                .thenReturn(Map.of(NotificationType.PASSWORD_CHANGED, stored));

        List<NotificationPreference> prefs = service.getPreferences("alice");

        NotificationPreference pref = prefs.stream()
                .filter(p -> p.type() == NotificationType.PASSWORD_CHANGED)
                .findFirst().orElseThrow();
        assertThat(pref.inAppEnabled()).isFalse();
        assertThat(pref.emailEnabled()).isTrue();
    }

    @Test
    void getPreferences_retorna_default_para_tipos_sem_preferencia_armazenada() {
        NotificationPreference stored = new NotificationPreference("alice", NotificationType.PASSWORD_CHANGED, false, false);
        when(preferenceRepository.findByUsername("alice"))
                .thenReturn(Map.of(NotificationType.PASSWORD_CHANGED, stored));

        List<NotificationPreference> prefs = service.getPreferences("alice");

        long defaultCount = prefs.stream()
                .filter(p -> p.type() != NotificationType.PASSWORD_CHANGED)
                .filter(p -> p.inAppEnabled() && p.emailEnabled())
                .count();
        assertThat(defaultCount).isEqualTo(NotificationType.values().length - 1);
    }

    @Test
    void updatePreference_persiste_preferencia_correta() {
        ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);

        service.updatePreference("alice", NotificationType.ACCOUNT_LOCKED, false, true);

        verify(preferenceRepository).upsert(captor.capture());
        NotificationPreference saved = captor.getValue();
        assertThat(saved.username()).isEqualTo("alice");
        assertThat(saved.type()).isEqualTo(NotificationType.ACCOUNT_LOCKED);
        assertThat(saved.inAppEnabled()).isFalse();
        assertThat(saved.emailEnabled()).isTrue();
    }
}
