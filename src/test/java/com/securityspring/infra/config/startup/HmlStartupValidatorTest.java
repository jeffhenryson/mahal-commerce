package com.securityspring.infra.config.startup;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa que {@link HmlStartupValidator} rejeita configurações inválidas de staging.
 */
class HmlStartupValidatorTest {

    private HmlStartupValidator validatorComProps(
            String jwtSecret, String dbPassword, String redisPassword,
            String resendKey, String resendFrom, String corsOrigins) {

        HmlStartupValidator v = new HmlStartupValidator();
        ReflectionTestUtils.setField(v, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(v, "dbPassword", dbPassword);
        ReflectionTestUtils.setField(v, "redisPassword", redisPassword);
        ReflectionTestUtils.setField(v, "resendApiKey", resendKey);
        ReflectionTestUtils.setField(v, "resendFrom", resendFrom);
        ReflectionTestUtils.setField(v, "corsAllowedOrigins", corsOrigins);
        ReflectionTestUtils.setField(v, "googleClientId", "123456789-abc.apps.googleusercontent.com");
        ReflectionTestUtils.setField(v, "totpEncryptionKey", "c2VjcmV0LXRlc3Qta2V5LXNlZ3VyYS1obWwtMzI=");
        return v;
    }

    private HmlStartupValidator validadorValido() {
        return validatorComProps(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "senha-segura-hml",
                "redis-senha-hml",
                "re_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "noreply@meudominio.com",
                "https://hml.meudominio.com"
        );
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    void deve_aceitar_configuracao_valida() {
        assertThatCode(() -> validadorValido().validate()).doesNotThrowAnyException();
    }

    // ── jwt.secret ───────────────────────────────────────────────────────────

    @Test
    void deve_rejeitar_jwt_secret_com_valor_dev() {
        HmlStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "jwtSecret", "dev-secret-please-change");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret");
    }

    // ── datasource password ───────────────────────────────────────────────────

    @Test
    void deve_rejeitar_datasource_password_vazio() {
        HmlStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "dbPassword", "");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }

    // ── redis password ────────────────────────────────────────────────────────

    @Test
    void deve_rejeitar_redis_password_vazio() {
        HmlStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "redisPassword", "");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.data.redis.password");
    }

    // ── resend ────────────────────────────────────────────────────────────────

    @Test
    void deve_rejeitar_resend_api_key_placeholder() {
        HmlStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "resendApiKey", "dev-placeholder-key");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resend.api-key");
    }

    @Test
    void deve_rejeitar_resend_from_com_dominio_reservado() {
        HmlStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "resendFrom", "noreply@example.com");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resend.from");
    }

    // ── cors ─────────────────────────────────────────────────────────────────

    @Test
    void deve_rejeitar_cors_wildcard_em_hml() {
        HmlStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "corsAllowedOrigins", "*");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cors.allowed-origins");
    }

    @Test
    void deve_rejeitar_cors_ausente_em_hml() {
        HmlStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "corsAllowedOrigins", "");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cors.allowed-origins");
    }

    // ── oauth2.google.client-id ───────────────────────────────────────────────

    @Test
    void deve_rejeitar_google_client_id_ausente() {
        HmlStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "googleClientId", "");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oauth2.google.client-id");
    }

    // ── totp.encryption.key ───────────────────────────────────────────────────

    @Test
    void deve_rejeitar_totp_encryption_key_ausente() {
        HmlStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "totpEncryptionKey", "");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("totp.encryption.key");
    }

    @Test
    void deve_rejeitar_totp_encryption_key_curta_demais() {
        HmlStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "totpEncryptionKey", "chave-curta");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("totp.encryption.key");
    }

    @Test
    void deve_rejeitar_totp_encryption_key_com_valor_inseguro_de_zeros() {
        HmlStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "totpEncryptionKey", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("totp.encryption.key");
    }

    // ── múltiplos erros ───────────────────────────────────────────────────────

    @Test
    void deve_reportar_todos_os_erros_de_uma_vez() {
        HmlStartupValidator v = validatorComProps(
                "dev-secret",          // jwt inválido
                "",                    // db password vazio
                "",                    // redis password vazio
                "dev-placeholder-key", // resend inválido
                "noreply@example.com", // from inválido
                "*"                    // CORS wildcard
        );
        ReflectionTestUtils.setField(v, "googleClientId", "");
        ReflectionTestUtils.setField(v, "totpEncryptionKey", "");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContainingAll(
                        "jwt.secret",
                        "spring.datasource.password",
                        "spring.data.redis.password",
                        "resend.api-key",
                        "resend.from",
                        "cors.allowed-origins",
                        "oauth2.google.client-id",
                        "totp.encryption.key");
    }
}
