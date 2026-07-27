package com.cernecommerce.infra.config.startup;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa que {@link DevStartupValidator} (PLAT-C032) bloqueia o boot quando o perfil dev é
 * ativado sobre um ambiente que parece ser de hml/produção, sem atrapalhar o dev local.
 *
 * <p>Segue o padrão de {@link ProdStartupValidatorTest}: instancia o validator diretamente e
 * injeta os campos via {@link ReflectionTestUtils}, sem subir um contexto Spring.</p>
 */
class DevStartupValidatorTest {

    /** Ambiente local típico: H2 em memória, CORS curinga, nenhuma variável de infra remota. */
    private DevStartupValidator validadorLocal() {
        DevStartupValidator v = new DevStartupValidator();
        ReflectionTestUtils.setField(v, "allowRemoteInfra", false);
        ReflectionTestUtils.setField(v, "dbUrl", "");
        ReflectionTestUtils.setField(v, "corsAllowedOrigins", "");
        ReflectionTestUtils.setField(v, "resolvedDatasourceUrl",
                "jdbc:h2:mem:demo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        return v;
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    void deve_aceitar_ambiente_local_padrao() {
        assertThatCode(() -> validadorLocal().validate()).doesNotThrowAnyException();
    }

    @Test
    void deve_aceitar_postgres_local_em_docker() {
        DevStartupValidator v = validadorLocal();
        ReflectionTestUtils.setField(v, "dbUrl", "jdbc:postgresql://localhost:5432/security");
        ReflectionTestUtils.setField(v, "resolvedDatasourceUrl", "jdbc:postgresql://localhost:5432/security");
        assertThatCode(v::validate).doesNotThrowAnyException();
    }

    @Test
    void deve_aceitar_cors_de_frontend_local() {
        DevStartupValidator v = validadorLocal();
        ReflectionTestUtils.setField(v, "corsAllowedOrigins", "http://localhost:3000");
        assertThatCode(v::validate).doesNotThrowAnyException();
    }

    // ── sinais de infraestrutura remota ──────────────────────────────────────

    /**
     * O cenário do PLAT-C032: o deploy define DB_URL apontando para o banco real, esquece
     * SPRING_PROFILES_ACTIVE, e a aplicação sobe sobre um H2 vazio ignorando a variável.
     */
    @Test
    void deve_rejeitar_db_url_remoto_ignorado_pelo_perfil_dev() {
        DevStartupValidator v = validadorLocal();
        ReflectionTestUtils.setField(v, "dbUrl", "jdbc:postgresql://db.mahaltabacaria.com.br:5432/security");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL")
                .hasMessageContaining("SPRING_PROFILES_ACTIVE");
    }

    @Test
    void deve_rejeitar_cors_com_origem_remota() {
        DevStartupValidator v = validadorLocal();
        ReflectionTestUtils.setField(v, "corsAllowedOrigins", "https://mahaltabacaria.com.br");
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    /** Se o perfil dev realmente usa o banco remoto, não há variável ignorada — não é o bug. */
    @Test
    void deve_aceitar_db_url_remoto_quando_e_a_fonte_de_dados_em_uso() {
        DevStartupValidator v = validadorLocal();
        String remoto = "jdbc:postgresql://db.staging.interno:5432/security";
        ReflectionTestUtils.setField(v, "dbUrl", remoto);
        ReflectionTestUtils.setField(v, "resolvedDatasourceUrl", remoto);
        assertThatCode(v::validate).doesNotThrowAnyException();
    }

    // ── escape hatch ─────────────────────────────────────────────────────────

    @Test
    void deve_aceitar_infra_remota_quando_liberado_explicitamente() {
        DevStartupValidator v = validadorLocal();
        ReflectionTestUtils.setField(v, "allowRemoteInfra", true);
        ReflectionTestUtils.setField(v, "dbUrl", "jdbc:postgresql://db.mahaltabacaria.com.br:5432/security");
        ReflectionTestUtils.setField(v, "corsAllowedOrigins", "https://mahaltabacaria.com.br");
        assertThatCode(v::validate).doesNotThrowAnyException();
    }
}
