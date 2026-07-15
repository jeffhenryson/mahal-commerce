package com.cernecommerce.infra.notification;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.NotificationPreferenceUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationUseCase notificationUseCase;
    private final NotificationPreferenceUseCase preferenceUseCase;
    private final UserRepository userRepository;
    private final EmailPort emailPort;
    private final NotificationSsePort ssePort;

    public NotificationEventListener(NotificationUseCase notificationUseCase,
                                     NotificationPreferenceUseCase preferenceUseCase,
                                     UserRepository userRepository,
                                     EmailPort emailPort,
                                     NotificationSsePort ssePort) {
        this.notificationUseCase = notificationUseCase;
        this.preferenceUseCase = preferenceUseCase;
        this.userRepository = userRepository;
        this.emailPort = emailPort;
        this.ssePort = ssePort;
    }

    @EventListener
    @Async("taskExecutor")
    public void onAuditEvent(AuditEvent event) {
        switch (event.type()) {
            case USER_PASSWORD_CHANGED -> {
                dispatch(event.username(), NotificationType.PASSWORD_CHANGED,
                        "Senha alterada", "Sua senha foi alterada. Se não foi você, contate o suporte.",
                        to -> emailPort.sendPasswordChangedAlert(to, event.username()));
            }
            case ACCOUNT_LOCKED -> {
                dispatch(event.username(), NotificationType.ACCOUNT_LOCKED,
                        "Conta bloqueada", "Sua conta foi bloqueada por excesso de tentativas.",
                        to -> emailPort.sendAccountLockedAlert(to, event.username()));
            }
            case TOTP_ENABLED -> {
                dispatch(event.username(), NotificationType.TOTP_ENABLED,
                        "Autenticação 2FA ativada", "A verificação em duas etapas foi ativada na sua conta.",
                        to -> emailPort.sendTotpStatusAlert(to, event.username(), true));
            }
            case TOTP_DISABLED -> {
                dispatch(event.username(), NotificationType.TOTP_DISABLED,
                        "Autenticação 2FA desativada", "A verificação em duas etapas foi desativada na sua conta.",
                        to -> emailPort.sendTotpStatusAlert(to, event.username(), false));
            }
            case TOKEN_THEFT_DETECTED -> {
                dispatch(event.username(), NotificationType.TOKEN_THEFT_DETECTED,
                        "Atividade suspeita detectada", "Detectamos uso suspeito do seu token de acesso. Todas as sessões foram encerradas.",
                        to -> emailPort.sendTokenTheftAlert(to, event.username()));
            }
            case USER_EMAIL_CHANGED -> {
                dispatch(event.username(), NotificationType.EMAIL_CHANGED,
                        "Email alterado", "O endereço de email da sua conta foi alterado.", null);
            }
            case USER_ROLE_ASSIGNED -> {
                String role = String.valueOf(event.details().get("role"));
                dispatch(event.username(), NotificationType.ROLE_ASSIGNED,
                        "Papel atribuído", "O papel " + role + " foi atribuído à sua conta.", null);
            }
            case USER_ROLE_REMOVED -> {
                String role = String.valueOf(event.details().get("role"));
                dispatch(event.username(), NotificationType.ROLE_REMOVED,
                        "Papel removido", "O papel " + role + " foi removido da sua conta.", null);
            }
            case USER_DISABLED -> {
                dispatch(event.username(), NotificationType.ACCOUNT_DISABLED,
                        "Conta desativada", "Sua conta foi desativada por um administrador.", null);
            }
            default -> { }
        }
    }

    private void dispatch(String username, NotificationType type, String title, String body,
                          Consumer<String> emailAction) {
        NotificationPreference pref = resolvePreference(username, type);
        if (pref.inAppEnabled()) {
            persist(username, type, title, body);
        }
        if (emailAction != null && pref.emailEnabled()) {
            sendEmail(username, emailAction);
        }
    }

    private NotificationPreference resolvePreference(String username, NotificationType type) {
        try {
            List<NotificationPreference> prefs = preferenceUseCase.getPreferences(username);
            return prefs.stream()
                    .filter(p -> p.type() == type)
                    .findFirst()
                    .orElseGet(() -> NotificationPreference.defaultFor(username, type));
        } catch (Exception ex) {
            log.warn("notification.preference.resolve.failed username={} type={} — using defaults", username, type);
            return NotificationPreference.defaultFor(username, type);
        }
    }

    private void persist(String username, NotificationType type, String title, String body) {
        try {
            Notification saved = notificationUseCase.notify(username, type, title, body);
            ssePort.send(username, saved);
        } catch (Exception ex) {
            log.error("notification.persist.failed username={} type={} error={}", username, type, ex.getMessage());
        }
    }

    private void sendEmail(String username, Consumer<String> sendAction) {
        try {
            userRepository.findByUsername(username)
                    .map(u -> u.getEmail())
                    .filter(email -> email != null && !email.isBlank())
                    .ifPresent(sendAction);
        } catch (Exception ex) {
            log.error("notification.email.failed username={} error={}", username, ex.getMessage());
        }
    }
}
