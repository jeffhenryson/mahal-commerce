package com.securityspring.adapter.out.email;

import com.securityspring.core.ports.out.notification.EmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter de email para desenvolvimento: exibe o código no log em vez de enviá-lo.
 * Em hml/prod, substituído pelo ResendEmailAdapter.
 *
 * Também retém o último código enviado por username para que testes de integração
 * possam recuperar o código em texto puro sem precisar inverter o hash.
 */
@Component
@Profile("dev")
public class LoggingEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailAdapter.class);

    private final Map<String, String> lastCodeByUsername = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationCode(String to, String username, String code) {
        log.info("DEV EMAIL >> to={} username={} verificationCode={}", to, username, code);
        lastCodeByUsername.put(username, code);
    }

    @Override
    public void sendPasswordResetLink(String to, String username, String resetLink) {
        log.info("DEV EMAIL >> to={} username={} passwordResetLink={}", to, username, resetLink);
        lastCodeByUsername.put("reset:" + username, resetLink);
    }

    @Override
    public void sendEmailChangeNotification(String oldEmail, String username, String newEmail) {
        log.info("DEV EMAIL >> oldEmail={} username={} newEmail={} [email-change-notification]",
                oldEmail, username, newEmail);
    }

    /** Returns the last plain-text verification code or reset link sent to the given username. Test use only. */
    public String getLastCodeForUsername(String username) {
        return lastCodeByUsername.get(username);
    }

    /** Returns the last password-reset link sent for the given username. Test use only. */
    public String getLastResetLinkForUsername(String username) {
        return lastCodeByUsername.get("reset:" + username);
    }
}
