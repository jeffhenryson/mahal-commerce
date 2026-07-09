package com.cernecommerce.infra.security.support;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/** Helper para testes de integração — manipula refresh tokens via JDBC puro. */
@Component
@Profile("dev")
public class RefreshTokenTestHelper {

    private final JdbcTemplate jdbc;

    public RefreshTokenTestHelper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void expireTokenByHash(String tokenHash) {
        jdbc.update("UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
                Timestamp.from(Instant.now().minusSeconds(10)), tokenHash);
    }
}
