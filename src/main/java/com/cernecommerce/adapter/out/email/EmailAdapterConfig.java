package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.ports.out.notification.EmailPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

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
            @Value("${email.verification.subject:Código de confirmação de cadastro}") String emailSubject,
            @Value("${email.verification.frontend-url:http://localhost:4200/auth/verify-email}") String verificationFrontendUrl,
            ThymeleafEmailRenderer renderer,
            MeterRegistry meterRegistry) {
        RestClient restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        return new ResendEmailAdapter(restClient, fromAddress, ttlMinutes, emailSubject, verificationFrontendUrl, renderer, meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "email.provider", havingValue = "mailpit")
    MailpitEmailAdapter mailpitEmailAdapter(
            @Value("${mailpit.from:noreply@cernedsgn.xyz}") String fromAddress,
            @Value("${mailpit.api-url:http://mailpit-mahal:8025/api/v1/send}") String apiUrl,
            @Value("${email.verification.ttl-minutes:15}") long ttlMinutes,
            @Value("${email.verification.subject:Código de confirmação de cadastro}") String emailSubject,
            @Value("${email.verification.frontend-url:http://localhost:4201/auth/verify-email}") String verificationFrontendUrl,
            ThymeleafEmailRenderer renderer,
            MeterRegistry meterRegistry) {
        RestClient restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
        return new MailpitEmailAdapter(restClient, fromAddress, ttlMinutes, emailSubject, verificationFrontendUrl, renderer, meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(EmailPort.class)
    LoggingEmailAdapter loggingEmailAdapter() {
        return new LoggingEmailAdapter();
    }
}
