package com.securityspring.infra.notification;

import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.domain.model.notification.NotificationType;
import com.securityspring.core.ports.in.NotificationUseCase;
import com.securityspring.core.ports.out.notification.EmailPort;
import com.securityspring.core.ports.out.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationUseCase notificationUseCase;
    private final UserRepository userRepository;
    private final EmailPort emailPort;

    public NotificationEventListener(NotificationUseCase notificationUseCase,
                                     UserRepository userRepository,
                                     EmailPort emailPort) {
        this.notificationUseCase = notificationUseCase;
        this.userRepository = userRepository;
        this.emailPort = emailPort;
    }

    @EventListener
    public void onAuditEvent(AuditEvent event) {
        switch (event.type()) {
            case USER_PASSWORD_CHANGED -> {
                persist(event.username(), NotificationType.PASSWORD_CHANGED,
                        "Senha alterada", "Sua senha foi alterada. Se não foi você, contate o suporte.");
                sendEmail(event.username(), to -> emailPort.sendPasswordChangedAlert(to, event.username()));
            }
            case ACCOUNT_LOCKED -> {
                persist(event.username(), NotificationType.ACCOUNT_LOCKED,
                        "Conta bloqueada", "Sua conta foi bloqueada por excesso de tentativas.");
                sendEmail(event.username(), to -> emailPort.sendAccountLockedAlert(to, event.username()));
            }
            case TOTP_ENABLED -> {
                persist(event.username(), NotificationType.TOTP_ENABLED,
                        "Autenticação 2FA ativada", "A verificação em duas etapas foi ativada na sua conta.");
                sendEmail(event.username(), to -> emailPort.sendTotpStatusAlert(to, event.username(), true));
            }
            case TOTP_DISABLED -> {
                persist(event.username(), NotificationType.TOTP_DISABLED,
                        "Autenticação 2FA desativada", "A verificação em duas etapas foi desativada na sua conta.");
                sendEmail(event.username(), to -> emailPort.sendTotpStatusAlert(to, event.username(), false));
            }
            case TOKEN_THEFT_DETECTED -> {
                persist(event.username(), NotificationType.TOKEN_THEFT_DETECTED,
                        "Atividade suspeita detectada", "Detectamos uso suspeito do seu token de acesso. Todas as sessões foram encerradas.");
                sendEmail(event.username(), to -> emailPort.sendTokenTheftAlert(to, event.username()));
            }
            case USER_EMAIL_CHANGED -> {
                persist(event.username(), NotificationType.EMAIL_CHANGED,
                        "Email alterado", "O endereço de email da sua conta foi alterado.");
            }
            case USER_ROLE_ASSIGNED -> {
                String role = String.valueOf(event.details().get("role"));
                persist(event.username(), NotificationType.ROLE_ASSIGNED,
                        "Papel atribuído", "O papel " + role + " foi atribuído à sua conta.");
            }
            case USER_ROLE_REMOVED -> {
                String role = String.valueOf(event.details().get("role"));
                persist(event.username(), NotificationType.ROLE_REMOVED,
                        "Papel removido", "O papel " + role + " foi removido da sua conta.");
            }
            case USER_DISABLED -> {
                persist(event.username(), NotificationType.ACCOUNT_DISABLED,
                        "Conta desativada", "Sua conta foi desativada por um administrador.");
            }
            default -> { }
        }
    }

    private void persist(String username, NotificationType type, String title, String body) {
        try {
            notificationUseCase.notify(username, type, title, body);
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
