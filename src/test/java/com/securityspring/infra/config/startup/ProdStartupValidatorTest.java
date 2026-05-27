package com.securityspring.infra.config.startup;

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
