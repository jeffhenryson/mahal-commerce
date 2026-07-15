package com.cernecommerce.infra.metrics;

import com.cernecommerce.core.domain.event.AuditEvent;
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
    private final Counter devElevations;
    private final Counter totpEnabled;
    private final Counter totpDisabled;
    private final Counter roleAssigned;
    private final Counter permissionAssigned;
    private final Counter permissionRemoved;

    public SecurityMetricsEventListener(MeterRegistry registry) {
        this.loginSuccess        = registry.counter("auth.login.total", "result", "success");
        this.loginFailure        = registry.counter("auth.login.total", "result", "failure");
        this.oauthLoginSuccess   = registry.counter("auth.oauth.login.total", "provider", "google");
        this.usersRegistered     = registry.counter("users.registered.total");
        this.emailsVerified      = registry.counter("users.email_verified.total");
        this.accountsLocked      = registry.counter("auth.account.locked.total");
        this.tokenTheftDetected  = registry.counter("auth.token.theft.total");
        this.sessionsCleared     = registry.counter("auth.sessions.cleared.total");
        this.passwordResets      = registry.counter("auth.password_reset.completed.total");
        this.devElevations       = registry.counter("auth.dev_elevation.total");
        this.totpEnabled         = registry.counter("users.totp_enabled.total");
        this.totpDisabled        = registry.counter("users.totp_disabled.total");
        this.roleAssigned        = registry.counter("rbac.role_assigned.total");
        this.permissionAssigned  = registry.counter("rbac.permission_assigned.total");
        this.permissionRemoved   = registry.counter("rbac.permission_removed.total");
    }

    @EventListener
    public void onAuditEvent(AuditEvent event) {
        switch (event.type()) {
            case USER_LOGGED_IN                -> loginSuccess.increment();
            case LOGIN_FAILED                  -> loginFailure.increment();
            case OAUTH_GOOGLE_LOGIN            -> oauthLoginSuccess.increment();
            case USER_REGISTERED               -> usersRegistered.increment();
            case USER_EMAIL_VERIFIED           -> emailsVerified.increment();
            case ACCOUNT_LOCKED                -> accountsLocked.increment();
            case TOKEN_THEFT_DETECTED          -> tokenTheftDetected.increment();
            case USER_SESSIONS_CLEARED         -> sessionsCleared.increment();
            case PASSWORD_RESET_COMPLETED      -> passwordResets.increment();
            case DEV_ELEVATION_COMPLETED       -> devElevations.increment();
            case TOTP_ENABLED                  -> totpEnabled.increment();
            case TOTP_DISABLED                 -> totpDisabled.increment();
            case USER_ROLE_ASSIGNED            -> roleAssigned.increment();
            case PERMISSION_ASSIGNED_TO_ROLE   -> permissionAssigned.increment();
            case PERMISSION_REMOVED_FROM_ROLE  -> permissionRemoved.increment();
            default -> { }
        }
    }
}
