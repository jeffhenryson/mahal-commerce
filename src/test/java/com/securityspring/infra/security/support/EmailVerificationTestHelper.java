package com.securityspring.infra.security.support;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Helper para testes de integração — recupera códigos de verificação de email via JDBC. */
@Component
@Profile("dev")
public class EmailVerificationTestHelper {

    private final JdbcTemplate jdbc;

    public EmailVerificationTestHelper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String getCodeForUsername(String username) {
        return jdbc.queryForObject(
                "SELECT code FROM email_verification_codes WHERE username = ? AND used = false ORDER BY id DESC LIMIT 1",
                String.class,
                username);
    }
}
