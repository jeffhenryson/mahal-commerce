package com.securityspring.infra.audit;

import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.domain.event.AuditEvent.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuthenticationEventsListener implements ApplicationListener<AbstractAuthenticationEvent> {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationEventsListener.class);

    private final ApplicationEventPublisher publisher;

    public AuthenticationEventsListener(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        var auth = event.getAuthentication();
        String username = auth != null ? auth.getName() : "<unknown>";

        if (event instanceof AuthenticationSuccessEvent) {
            log.info("auth.success user={}", username);
        } else if (event instanceof AbstractAuthenticationFailureEvent failure) {
            String reason = failure.getException().getClass().getSimpleName();
            log.warn("auth.failure user={} reason={}", username, reason);
            // Só registra LOGIN_FAILED para tentativas de login com username/password.
            // Falhas de outros mecanismos (ex: bearer token expirado) não são falhas de login
            // e gerariam falsos positivos no audit log e no potencial lockout por tentativas.
            if (auth instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken) {
                publisher.publishEvent(AuditEvent.of(EventType.LOGIN_FAILED, username,
                        Map.of("reason", reason)));
            }
        } else {
            log.debug("auth.event type={} user={}", event.getClass().getSimpleName(), username);
        }
    }
}
