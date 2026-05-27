package com.securityspring.infra.config.startup;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Valida variáveis de ambiente obrigatórias na inicialização do perfil prod.
 * Falha com mensagem clara antes que a aplicação aceite tráfego, evitando
 * que um deploy mal configurado exponha endpoints com credenciais padrão.
 */
@Configuration
@Profile("prod")
public class ProdStartupValidator {

    @Value("${spring.application.name:}")
    private String applicationName;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.issuer:security-spring}")
    private String jwtIssuer;

    @Value("${jwt.audience:api}")
    private String jwtAudience;

    @Value("${cors.allowed-origins}")
    private String corsAllowedOrigins;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${resend.api-key}")
    private String resendApiKey;

    @Value("${resend.from:}")
    private String resendFrom;

    @PostConstruct
    public void validate() {
        List<String> errors = new ArrayList<>();

        if (isBlankOrPlaceholder(jwtSecret, "dev-secret", "troque-para")) {
            errors.add("jwt.secret está ausente ou contém valor de desenvolvimento");
        } else if (jwtSecret != null && jwtSecret.length() < 44) {
            // Uma chave HS256 precisa de ao menos 256 bits (32 bytes).
            // Representada em Base64 sem padding, 32 bytes → 43 chars (ceil(32*4/3)).
            // Verificar o comprimento da string é proxy conservador: chaves mais curtas
            // não têm entropia suficiente independentemente da codificação.
            errors.add("jwt.secret parece curto demais para HS256 — use ao menos 44 caracteres (Base64 de 256 bits)");
        }
        if (isBlankOrPlaceholder(corsAllowedOrigins, "*")) {
            errors.add("cors.allowed-origins não pode ser '*' em produção — defina CORS_ALLOWED_ORIGINS");
        }
        if (isBlankOrPlaceholder(dbUrl, "localhost", "h2:mem")) {
            errors.add("spring.datasource.url parece apontar para ambiente de desenvolvimento");
        }
        if (isBlankOrPlaceholder(resendApiKey, "placeholder", "dev-")) {
            errors.add("resend.api-key está ausente ou contém valor de desenvolvimento");
        }
        if (isBlankOrPlaceholder(resendFrom, "example.com")) {
            errors.add("resend.from (RESEND_FROM) está ausente ou usa domínio reservado — emails serão rejeitados");
        }
        if ("security-spring".equalsIgnoreCase(jwtIssuer)) {
            errors.add("jwt.issuer está com o valor padrão 'security-spring' — defina JWT_ISSUER com o nome do seu serviço");
        }
        if ("api".equalsIgnoreCase(jwtAudience)) {
            errors.add("jwt.audience está com o valor padrão 'api' — defina JWT_AUDIENCE com um identificador único para este serviço");
        }
        if ("security-spring".equalsIgnoreCase(applicationName) || isBlankOrPlaceholder(applicationName)) {
            errors.add("spring.application.name ainda é 'security-spring' (nome do template) — renomeie em application.properties e no artifactId do pom.xml");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "\n\n[PROD STARTUP VALIDATION FAILED] Variáveis obrigatórias inválidas ou ausentes:\n  - "
                    + String.join("\n  - ", errors)
                    + "\n\nVerifique as variáveis de ambiente antes de iniciar em produção.\n");
        }
    }

    private boolean isBlankOrPlaceholder(String value, String... fragments) {
        if (value == null || value.isBlank()) return true;
        String lower = value.toLowerCase();
        for (String fragment : fragments) {
            if (lower.contains(fragment.toLowerCase())) return true;
        }
        return false;
    }
}
