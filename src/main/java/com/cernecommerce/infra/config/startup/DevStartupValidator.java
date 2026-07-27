package com.cernecommerce.infra.config.startup;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Contraparte de {@link ProdStartupValidator} para o perfil dev (PLAT-C032).
 *
 * <p>O perfil default é {@code dev} ({@code application.properties}:
 * {@code spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}}), então um deploy que esqueça
 * a variável sobe permissivo e <b>em silêncio</b>: H2 em memória (o driver tem escopo
 * {@code runtime}, logo embarca no jar), CORS {@code *}, CSP vazio, console H2 aberto e seed
 * com senha do repositório. O aviso em comentário não impedia nada.</p>
 *
 * <p>Em vez de tentar provar "isto é produção" — impossível a partir de dentro do processo —
 * este validador procura <i>sinais de infraestrutura remota</i>: variáveis que só os perfis
 * hml/prod consomem e que, no perfil dev, seriam <b>silenciosamente ignoradas</b>. Esse é
 * exatamente o cenário perigoso: o operador acredita ter configurado o banco de produção, e a
 * aplicação sobe sobre um H2 vazio.</p>
 *
 * <p>Quem legitimamente aponta o ambiente local para infraestrutura remota (um banco de
 * staging compartilhado, por exemplo) pode liberar o boot com
 * {@code DEV_ALLOW_REMOTE_INFRA=true} — a intenção fica explícita em vez de implícita.</p>
 *
 * <p>Complementa {@link DevModeWarningConfig}, que apenas <i>avisa</i> no log após a subida —
 * suficiente para o dev local, inútil num deploy onde ninguém lê o console. Este validador
 * roda em {@code @PostConstruct}, portanto aborta antes daquele banner ser emitido.</p>
 */
@Configuration
@Profile("dev")
public class DevStartupValidator {

    @Value("${DEV_ALLOW_REMOTE_INFRA:false}")
    private boolean allowRemoteInfra;

    /** Consumida por {@code application-prod/hml.properties}; no perfil dev é ignorada. */
    @Value("${DB_URL:}")
    private String dbUrl;

    @Value("${CORS_ALLOWED_ORIGINS:}")
    private String corsAllowedOrigins;

    /** URL efetivamente em uso — em dev, tipicamente H2 em memória. */
    @Value("${spring.datasource.url:}")
    private String resolvedDatasourceUrl;

    @PostConstruct
    public void validate() {
        if (allowRemoteInfra) {
            return;
        }

        List<String> signals = new ArrayList<>();

        if (isRemote(dbUrl) && !isRemote(resolvedDatasourceUrl)) {
            signals.add("DB_URL aponta para um banco remoto, mas o perfil dev está usando "
                    + "outra fonte de dados (provavelmente H2 em memória) — a variável foi ignorada");
        }
        if (isRemoteOrigin(corsAllowedOrigins)) {
            signals.add("CORS_ALLOWED_ORIGINS define uma origem remota, mas o perfil dev "
                    + "libera CORS para '*'");
        }

        if (!signals.isEmpty()) {
            throw new IllegalStateException(
                    "\n\n[DEV STARTUP VALIDATION FAILED] A aplicação está subindo no perfil 'dev', "
                    + "mas o ambiente parece ser de hml/produção:\n  - "
                    + String.join("\n  - ", signals)
                    + "\n\nO perfil dev é permissivo: H2 em memória, CORS '*', CSP vazio, console H2 "
                    + "aberto e seed com senha versionada no repositório.\n"
                    + "Defina SPRING_PROFILES_ACTIVE=prod (ou hml) para este deploy.\n"
                    + "Se este ambiente local realmente usa infraestrutura remota de propósito, "
                    + "defina DEV_ALLOW_REMOTE_INFRA=true.\n");
        }
    }

    /** Verdadeiro para URLs de banco que não sejam embarcadas nem locais. */
    private boolean isRemote(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase();
        return !lower.contains("h2:mem")
                && !lower.contains("localhost")
                && !lower.contains("127.0.0.1");
    }

    /** Verdadeiro para origens CORS que não sejam curinga nem locais. */
    private boolean isRemoteOrigin(String origins) {
        if (origins == null || origins.isBlank() || "*".equals(origins.trim())) return false;
        String lower = origins.toLowerCase();
        return !lower.contains("localhost") && !lower.contains("127.0.0.1");
    }
}
