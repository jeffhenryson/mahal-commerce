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

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${cors.allowed-origins}")
    private String corsAllowedOrigins;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${resend.api-key}")
    private String resendApiKey;

    @PostConstruct
    public void validate() {
        List<String> errors = new ArrayList<>();

        if (isBlankOrPlaceholder(jwtSecret, "dev-secret", "troque-para")) {
            errors.add("jwt.secret está ausente ou contém valor de desenvolvimento");
        }
        if (jwtSecret != null && jwtSecret.getBytes().length < 32) {
            errors.add("jwt.secret deve ter ao menos 32 bytes (256 bits) para HS256");
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
