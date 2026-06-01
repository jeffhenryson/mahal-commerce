package com.securityspring.infra.metrics;

import com.securityspring.core.domain.event.AuditEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SecurityMetricsEventListener {

    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter oauthLoginSuccess;
    private final Counter usersRegistered;
    private final Counter emailsVerified;
    private final Counter accountsLocked;
    private final Counter tokenTheftDetected;
    private final Counter sessionsCleared;
    private final Counter passwordResets;

    public SecurityMetricsEventListener(MeterRegistry registry) {
        this.loginSuccess       = registry.counter("auth.login.total", "result", "success");
        this.loginFailure       = registry.counter("auth.login.total", "result", "failure");
        this.oauthLoginSuccess  = registry.counter("auth.oauth.login.total", "provider", "google");
        this.usersRegistered    = registry.counter("users.registered.total");
        this.emailsVerified     = registry.counter("users.email_verified.total");
        this.accountsLocked     = registry.counter("auth.account.locked.total");
        this.tokenTheftDetected = registry.counter("auth.token.theft.total");
        this.sessionsCleared    = registry.counter("auth.sessions.cleared.total");
        this.passwordResets     = registry.counter("auth.password_reset.completed.total");
    }

    @EventListener
    public void onAuditEvent(AuditEvent event) {
        switch (event.type()) {
            case USER_LOGGED_IN             -> loginSuccess.increment();
            case LOGIN_FAILED               -> loginFailure.increment();
            case OAUTH_GOOGLE_LOGIN         -> oauthLoginSuccess.increment();
            case USER_REGISTERED            -> usersRegistered.increment();
            case USER_EMAIL_VERIFIED        -> emailsVerified.increment();
            case ACCOUNT_LOCKED             -> accountsLocked.increment();
            case TOKEN_THEFT_DETECTED       -> tokenTheftDetected.increment();
            case USER_SESSIONS_CLEARED      -> sessionsCleared.increment();
            case PASSWORD_RESET_COMPLETED   -> passwordResets.increment();
            default -> { }
        }
    }
}
