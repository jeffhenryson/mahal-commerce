package com.security_spring.infra.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEventsListener implements ApplicationListener<AbstractAuthenticationEvent> {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationEventsListener.class);

    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        var auth = event.getAuthentication();
        String username = auth != null ? auth.getName() : "<unknown>";

        if (event instanceof AuthenticationSuccessEvent) {
            log.info("auth.success user={} details={}", username, auth.getDetails());
        } else if (event instanceof AbstractAuthenticationFailureEvent failure) {
            log.warn("auth.failure user={} reason={} details={}", username, failure.getException().getMessage(), auth.getDetails());
        } else {
            log.debug("auth.event type={} user={}", event.getClass().getSimpleName(), username);
        }
    }
}
