package com.securityspring.infra.config.startup;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Valida variáveis de ambiente obrigatórias na inicialização do perfil hml (staging).
 * Falha com mensagem clara antes que a aplicação aceite tráfego.
 */
@Configuration
@Profile("hml")
public class HmlStartupValidator {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from:}")
    private String resendFrom;

    @Value("${cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Value("${oauth2.google.client-id:}")
    private String googleClientId;

    @PostConstruct
    public void validate() {
        List<String> errors = new ArrayList<>();

        if (isBlankOrPlaceholder(jwtSecret, "dev-secret", "troque-para")) {
            errors.add("jwt.secret está ausente ou contém valor de desenvolvimento");
        }
        if (isBlankOrPlaceholder(dbPassword)) {
            errors.add("spring.datasource.password (DB_PASSWORD) não está definido");
        }
        if (isBlankOrPlaceholder(redisPassword)) {
            errors.add("spring.data.redis.password (REDIS_PASSWORD) não está definido");
        }
        if (isBlankOrPlaceholder(resendApiKey, "placeholder", "dev-")) {
            errors.add("resend.api-key está ausente ou contém valor de desenvolvimento");
        }
        if (isBlankOrPlaceholder(resendFrom, "example.com")) {
            errors.add("resend.from (RESEND_FROM) está ausente ou usa domínio reservado — emails serão rejeitados");
        }
        if (isBlankOrPlaceholder(corsAllowedOrigins, "*")) {
            errors.add("cors.allowed-origins não pode ser '*' em hml — defina CORS_ALLOWED_ORIGINS com a origem do ambiente de staging");
        }
        if (isBlankOrPlaceholder(googleClientId)) {
            errors.add("oauth2.google.client-id (GOOGLE_CLIENT_ID) está ausente — autenticação OAuth2 Google pode ser bypassada com qualquer token");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "\n\n[HML STARTUP VALIDATION FAILED] Variáveis obrigatórias inválidas ou ausentes:\n  - "
                    + String.join("\n  - ", errors)
                    + "\n\nVerifique as variáveis de ambiente antes de iniciar em hml.\n");
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
