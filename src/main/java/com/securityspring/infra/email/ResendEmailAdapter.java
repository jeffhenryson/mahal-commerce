package com.securityspring.infra.email;

import com.securityspring.core.ports.out.EmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@org.springframework.context.annotation.Profile({"hml", "prod"})
public class ResendEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailAdapter.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final RestClient restClient;
    private final String fromAddress;
    private final long ttlMinutes;

    public ResendEmailAdapter(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from:noreply@example.com}") String fromAddress,
            @Value("${email.verification.ttl-minutes:15}") long ttlMinutes) {
        this.fromAddress = fromAddress;
        this.ttlMinutes = ttlMinutes;
        this.restClient = RestClient.builder()
                .baseUrl(RESEND_API_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public void sendVerificationCode(String to, String username, String code) {
        String html = buildEmailHtml(username, code);
        Map<String, Object> body = Map.of(
                "from", fromAddress,
                "to", List.of(to),
                "subject", "Código de confirmação de cadastro",
                "html", html
        );
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("email.verification.sent to={}", to);
        } catch (Exception ex) {
            log.error("email.verification.failed to={} error={}", to, ex.getMessage());
            throw new RuntimeException("Falha ao enviar email de verificação", ex);
        }
    }

    private String buildEmailHtml(String username, String code) {
        return """
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto">
                  <h2>Confirmação de cadastro</h2>
                  <p>Olá, <strong>%s</strong>!</p>
                  <p>Use o código abaixo para ativar sua conta:</p>
                  <div style="font-size:2rem;font-weight:bold;letter-spacing:.3rem;
                              background:#f4f4f4;padding:16px;text-align:center;border-radius:8px">
                    %s
                  </div>
                  <p style="color:#666;font-size:.85rem">Este código expira em %d minutos.<br>
                  Se você não solicitou este cadastro, ignore este email.</p>
                </div>
                """.formatted(username, code, ttlMinutes);
    }
}
