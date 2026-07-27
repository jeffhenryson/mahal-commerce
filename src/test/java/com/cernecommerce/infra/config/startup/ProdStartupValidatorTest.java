package com.cernecommerce.infra.config.startup;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa que {@link ProdStartupValidator} rejeita configurações inválidas ou de desenvolvimento.
 *
 * <p>Instancia o validator diretamente e usa {@link ReflectionTestUtils} para injetar os campos
 * (simulando o que {@code @Value} faria em runtime), evitando a necessidade de subir um contexto
 * Spring com o perfil "prod" ativo.</p>
 */
class ProdStartupValidatorTest {

    private ProdStartupValidator validatorComProps(
            String appName, String jwtSecret, String jwtIssuer, String jwtAudience,
            String corsOrigins, String dbUrl, String resendKey, String resendFrom) {

        ProdStartupValidator v = new ProdStartupValidator();
        ReflectionTestUtils.setField(v, "applicationName", appName);
        ReflectionTestUtils.setField(v, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(v, "jwtIssuer", jwtIssuer);
        ReflectionTestUtils.setField(v, "jwtAudience", jwtAudience);
        ReflectionTestUtils.setField(v, "corsAllowedOrigins", corsOrigins);
        ReflectionTestUtils.setField(v, "dbUrl", dbUrl);
        ReflectionTestUtils.setField(v, "resendApiKey", resendKey);
        ReflectionTestUtils.setField(v, "resendFrom", resendFrom);
        // Base64 de 32 bytes (44 chars). Não pode ser o padrão de zeros ("AAAA...") — desde
        // PLAT-C028 o validator o trata como placeholder, como o HmlStartupValidator já fazia.
        ReflectionTestUtils.setField(v, "totpEncryptionKey", "cpp8ZZIhZSudh6UPD+OgTzqUhGhnFroAq285qGFEb9M=");
        ReflectionTestUtils.setField(v, "avatarBaseUrl", "https://cdn.meudominio.com/avatars");
        ReflectionTestUtils.setField(v, "googleClientId", "123456789-abc.apps.googleusercontent.com");
        return v;
    }

    private ProdStartupValidator validadorValido() {
        return validatorComProps(
                "meu-servico",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "meu-servico",
                "meu-servico-api",
                "https://meudominio.com",
                "jdbc:postgresql://postgres:5432/prod",
                "re_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "noreply@meudominio.com"
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
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "jwtSecret", "dev-secret-please-change");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret");
    }

    @Test
    void deve_rejeitar_jwt_secret_curto_demais() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "jwtSecret", "curto");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret");
    }

    // ── cors ──────────────────────────────────────────────────────────────────

    @Test
    void deve_rejeitar_cors_wildcard() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "corsAllowedOrigins", "*");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cors.allowed-origins");
    }

    // ── datasource ────────────────────────────────────────────────────────────

    @Test
    void deve_rejeitar_datasource_apontando_para_localhost() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "dbUrl", "jdbc:postgresql://localhost:5432/dev");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url");
    }

    @Test
    void deve_rejeitar_datasource_h2_em_memoria() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "dbUrl", "jdbc:h2:mem:demo");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url");
    }

    // ── resend ────────────────────────────────────────────────────────────────

    @Test
    void deve_rejeitar_resend_api_key_placeholder() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "resendApiKey", "dev-placeholder-key");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resend.api-key");
    }

    @Test
    void deve_rejeitar_resend_from_com_dominio_reservado() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "resendFrom", "noreply@example.com");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resend.from");
    }

    // ── jwt issuer / audience defaults ────────────────────────────────────────

    @Test
    void deve_rejeitar_jwt_issuer_default() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "jwtIssuer", "security-spring");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.issuer");
    }

    @Test
    void deve_rejeitar_jwt_audience_default() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "jwtAudience", "api");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.audience");
    }

    // ── application name ──────────────────────────────────────────────────────

    @Test
    void deve_rejeitar_application_name_padrao_do_template() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "applicationName", "security-spring");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.application.name");
    }

    // ── totp.encryption.key ───────────────────────────────────────────────────

    @Test
    void deve_rejeitar_totp_encryption_key_ausente() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "totpEncryptionKey", "");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("totp.encryption.key");
    }

    @Test
    void deve_rejeitar_totp_encryption_key_curto_demais() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "totpEncryptionKey", "curto");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("totp.encryption.key");
    }

    @Test
    void deve_rejeitar_totp_encryption_key_com_valor_default_comprometido() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "totpEncryptionKey", "Vx74sQn7CT7IQyr34DOxmhIT3zerUlBbeyOazfudKpU=");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("totp.encryption.key")
                .hasMessageContaining("comprometido");
    }

    /**
     * PLAT-C028: o fallback reintroduzido em {@code docker-compose.prod.yml} (commit ca2ee50)
     * não era reconhecido pelo validator, então o boot não bloqueava. Removê-lo do compose não
     * basta — o valor continua no histórico do git e pode ser copiado de volta para o .env.
     */
    @Test
    void deve_rejeitar_totp_encryption_key_do_fallback_reintroduzido_no_compose() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "totpEncryptionKey", "zNEtKjyPPpEkAIBZBZ29nixcQCcAcA19ExgMgaQVRjg=");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("totp.encryption.key")
                .hasMessageContaining("comprometido");
    }

    /**
     * PLAT-C028: o limite anterior (32 chars) aceitava Base64 de 24 bytes — 192 bits, abaixo
     * do necessário para AES-256. Base64 de 256 bits ocupa 44 caracteres.
     */
    @Test
    void deve_rejeitar_totp_encryption_key_com_192_bits() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "totpEncryptionKey", "M8Xk2pQvR7nLdT3yBw5zHfJc6mKgN9sA");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("totp.encryption.key")
                .hasMessageContaining("44");
    }

    /** O padrão de zeros em Base64 é placeholder de desenvolvimento, não uma chave. */
    @Test
    void deve_rejeitar_totp_encryption_key_base64_de_zeros() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "totpEncryptionKey", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("totp.encryption.key");
    }

    // ── avatar.base-url ───────────────────────────────────────────────────────

    @Test
    void deve_rejeitar_avatar_base_url_ausente() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "avatarBaseUrl", "");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("avatar.base-url");
    }

    @Test
    void deve_rejeitar_avatar_base_url_localhost() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "avatarBaseUrl", "http://localhost:8080/avatars");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("avatar.base-url");
    }

    @Test
    void deve_rejeitar_avatar_base_url_example_com() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "avatarBaseUrl", "https://example.com/avatars");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("avatar.base-url");
    }

    // ── oauth2.google.client-id ───────────────────────────────────────────────

    @Test
    void deve_rejeitar_google_client_id_ausente() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "googleClientId", "");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oauth2.google.client-id");
    }

    @Test
    void deve_rejeitar_google_client_id_nulo() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "googleClientId", null);
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oauth2.google.client-id");
    }

    // ── seed.dev.password (ROLE_DEV) ────────────────────────────────────────────

    @Test
    void deve_rejeitar_dev_email_definido_sem_dev_password() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "devEmail", "dev@meudominio.com");
        ReflectionTestUtils.setField(v, "devPassword", "");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seed.dev.password");
    }

    @Test
    void deve_rejeitar_dev_email_definido_com_dev_password_default() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "devEmail", "dev@meudominio.com");
        ReflectionTestUtils.setField(v, "devPassword", "Dev@secure1!");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seed.dev.password");
    }

    @Test
    void deve_aceitar_dev_email_definido_com_dev_password_real() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "devEmail", "dev@meudominio.com");
        ReflectionTestUtils.setField(v, "devPassword", "S3nhaReal!DoDev");
        assertThatCode(v::validate).doesNotThrowAnyException();
    }

    @Test
    void deve_aceitar_dev_password_default_quando_dev_email_ausente() {
        ProdStartupValidator v = validadorValido();
        ReflectionTestUtils.setField(v, "devEmail", "");
        ReflectionTestUtils.setField(v, "devPassword", "Dev@secure1!");
        assertThatCode(v::validate).doesNotThrowAnyException();
    }

    // ── múltiplos erros — todos reportados juntos ─────────────────────────────

    @Test
    void deve_reportar_todos_os_erros_de_uma_vez() {
        ProdStartupValidator v = validatorComProps(
                "security-spring",   // nome padrão do template
                "dev-secret",        // secret de dev
                "security-spring",   // issuer padrão
                "api",               // audience padrão
                "*",                 // CORS wildcard
                "jdbc:h2:mem:demo",  // H2 em memória
                "dev-placeholder-key",
                "noreply@example.com"
        );
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContainingAll(
                        "jwt.secret", "cors.allowed-origins",
                        "spring.datasource.url", "resend.api-key",
                        "jwt.issuer", "jwt.audience",
                        "spring.application.name");
    }
}
