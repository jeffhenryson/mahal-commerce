package com.securityspring.adapter.out.email;

import com.securityspring.core.ports.out.notification.EmailPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seleciona o adapter de e-mail conforme {@code email.provider}:
 * <ul>
 *   <li>{@code resend} — envia via API Resend (requer {@code resend.api-key} real).</li>
 *   <li>Qualquer outro valor / ausente — loga no console (padrão dev/testes).</li>
 * </ul>
 *
 * Usar @Bean em @Configuration garante que @ConditionalOnMissingBean seja avaliado
 * na ordem correta — diferente de @Component que tem ordenação não determinística.
 */
@Configuration
class EmailAdapterConfig {

    @Bean
    @ConditionalOnProperty(name = "email.provider", havingValue = "resend")
    ResendEmailAdapter resendEmailAdapter(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from:noreply@example.com}") String fromAddress,
            @Value("${resend.api-url:https://api.resend.com/emails}") String apiUrl,
            @Value("${email.verification.ttl-minutes:15}") long ttlMinutes,
            @Value("${email.verification.subject:Código de confirmação de cadastro}") String emailSubject) {
        return new ResendEmailAdapter(apiKey, fromAddress, apiUrl, ttlMinutes, emailSubject);
    }

    @Bean
    @ConditionalOnMissingBean(EmailPort.class)
    LoggingEmailAdapter loggingEmailAdapter() {
        return new LoggingEmailAdapter();
    }
}
