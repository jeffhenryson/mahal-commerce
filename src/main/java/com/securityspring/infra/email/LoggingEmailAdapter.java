package com.securityspring.infra.email;

import com.securityspring.core.ports.out.EmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Adapter de email para desenvolvimento: exibe o código no log em vez de enviá-lo.
 * Em hml/prod, substituído pelo ResendEmailAdapter.
 */
@Component
@Profile("dev")
public class LoggingEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailAdapter.class);

    @Override
    public void sendVerificationCode(String to, String username, String code) {
        log.info("DEV EMAIL >> to={} username={} verificationCode={}", to, username, code);
    }
}
