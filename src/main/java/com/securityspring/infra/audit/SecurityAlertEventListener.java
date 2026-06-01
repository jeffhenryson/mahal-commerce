package com.securityspring.infra.audit;

import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.ports.out.notification.EmailPort;
import com.securityspring.core.ports.out.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class SecurityAlertEventListener {

    private static final Logger log = LoggerFactory.getLogger(SecurityAlertEventListener.class);

    private final UserRepository userRepository;
    private final EmailPort emailPort;

    public SecurityAlertEventListener(UserRepository userRepository, EmailPort emailPort) {
        this.userRepository = userRepository;
        this.emailPort = emailPort;
    }

    @EventListener
    public void onAuditEvent(AuditEvent event) {
        switch (event.type()) {
            case USER_PASSWORD_CHANGED ->
                alertByUsername(event.username(), to -> emailPort.sendPasswordChangedAlert(to, event.username()));
            case ACCOUNT_LOCKED ->
                alertByUsername(event.username(), to -> emailPort.sendAccountLockedAlert(to, event.username()));
            case TOTP_ENABLED ->
                alertByUsername(event.username(), to -> emailPort.sendTotpStatusAlert(to, event.username(), true));
            case TOTP_DISABLED ->
                alertByUsername(event.username(), to -> emailPort.sendTotpStatusAlert(to, event.username(), false));
            case TOKEN_THEFT_DETECTED ->
                alertByUsername(event.username(), to -> emailPort.sendTokenTheftAlert(to, event.username()));
            default -> { }
        }
    }

    private void alertByUsername(String username, Consumer<String> sendAction) {
        try {
            userRepository.findByUsername(username)
                    .map(u -> u.getEmail())
                    .filter(email -> email != null && !email.isBlank())
                    .ifPresent(sendAction);
        } catch (Exception ex) {
            log.error("security.alert.failed username={} error={}", username, ex.getMessage());
        }
    }
}
