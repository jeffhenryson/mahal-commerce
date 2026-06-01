package com.securityspring.adapter.out.email;

import com.securityspring.core.domain.exception.email.EmailDeliveryException;
import com.securityspring.core.ports.out.notification.EmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@org.springframework.context.annotation.Profile({"hml", "prod"})
public class ResendEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailAdapter.class);
    private final RestClient restClient;
    private final String fromAddress;
    private final long ttlMinutes;

    private final String emailSubject;

    public ResendEmailAdapter(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from:noreply@example.com}") String fromAddress,
            @Value("${resend.api-url:https://api.resend.com/emails}") String apiUrl,
            @Value("${email.verification.ttl-minutes:15}") long ttlMinutes,
            @Value("${email.verification.subject:Código de confirmação de cadastro}") String emailSubject) {
        this.fromAddress = fromAddress;
        this.ttlMinutes = ttlMinutes;
        this.emailSubject = emailSubject;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendVerificationCode(String to, String username, String code) {
        String html = buildVerificationEmailHtml(username, code);
        send(to, emailSubject, html, "email.verification");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPasswordResetLink(String to, String username, String resetLink) {
        String html = buildPasswordResetEmailHtml(username, resetLink);
        send(to, "Recuperação de senha", html, "email.password-reset");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendEmailChangeNotification(String oldEmail, String username, String newEmail) {
        String html = buildEmailChangeNotificationHtml(username, newEmail);
        send(oldEmail, "Seu email foi alterado", html, "email.email-change-notification");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPasswordChangedAlert(String to, String username) {
        String html = buildSecurityAlertHtml(username,
                "Sua senha foi alterada",
                "A senha da sua conta foi alterada agora.",
                "Se não foi você, entre em contato com o suporte imediatamente e revogue suas sessões.");
        send(to, "Alerta de segurança: senha alterada", html, "email.security-alert.password-changed");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendAccountLockedAlert(String to, String username) {
        String html = buildSecurityAlertHtml(username,
                "Conta temporariamente bloqueada",
                "Sua conta foi bloqueada temporariamente devido a múltiplas tentativas de login malsucedidas.",
                "Aguarde alguns minutos e tente novamente. Se não foi você, sua senha pode estar comprometida.");
        send(to, "Alerta de segurança: conta bloqueada", html, "email.security-alert.account-locked");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendTotpStatusAlert(String to, String username, boolean enabled) {
        String action = enabled ? "ativada" : "desativada";
        String detail = enabled
                ? "A autenticação em dois fatores foi ativada na sua conta."
                : "A autenticação em dois fatores foi desativada na sua conta.";
        String html = buildSecurityAlertHtml(username,
                "Autenticação em dois fatores " + action,
                detail,
                "Se não foi você, acesse sua conta e revogue todas as sessões ativas imediatamente.");
        send(to, "Alerta de segurança: 2FA " + action, html, "email.security-alert.totp-" + (enabled ? "enabled" : "disabled"));
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendTokenTheftAlert(String to, String username) {
        String html = buildSecurityAlertHtml(username,
                "Acesso suspeito detectado",
                "Detectamos o reuso de uma credencial de sessão já utilizada — todas as sessões da sua conta foram encerradas automaticamente.",
                "Se não foi você, sua conta pode estar comprometida. Troque sua senha imediatamente.");
        send(to, "Alerta de segurança: acesso suspeito", html, "email.security-alert.token-theft");
    }

    private void send(String to, String subject, String html, String logPrefix) {
        Map<String, Object> body = Map.of(
                "from", fromAddress,
                "to", List.of(to),
                "subject", subject,
                "html", html
        );
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("{}.sent to={}", logPrefix, to);
        } catch (Exception ex) {
            log.error("{}.failed to={} error={}", logPrefix, to, ex.getMessage());
            throw new EmailDeliveryException(ex.getMessage());
        }
    }

    private String buildVerificationEmailHtml(String username, String code) {
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
                """.formatted(escapeHtml(username), escapeHtml(code), ttlMinutes);
    }

    private String buildPasswordResetEmailHtml(String username, String resetLink) {
        return """
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto">
                  <h2>Recuperação de senha</h2>
                  <p>Olá, <strong>%s</strong>!</p>
                  <p>Recebemos uma solicitação para redefinir a senha da sua conta.</p>
                  <p style="text-align:center">
                    <a href="%s"
                       style="display:inline-block;background:#0070f3;color:#fff;padding:12px 24px;
                              border-radius:6px;text-decoration:none;font-weight:bold">
                      Redefinir senha
                    </a>
                  </p>
                  <p style="color:#666;font-size:.85rem">
                    Este link expira em 15 minutos.<br>
                    Se você não solicitou a recuperação de senha, ignore este email — sua senha permanece a mesma.
                  </p>
                </div>
                """.formatted(escapeHtml(username), escapeHtml(resetLink));
    }

    private String buildEmailChangeNotificationHtml(String username, String newEmail) {
        return """
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto">
                  <h2>Email da conta alterado</h2>
                  <p>Olá, <strong>%s</strong>!</p>
                  <p>O email de acesso à sua conta foi alterado para <strong>%s</strong>.</p>
                  <p style="color:#666;font-size:.85rem">
                    Se você não realizou esta alteração, entre em contato com o suporte imediatamente.
                  </p>
                </div>
                """.formatted(escapeHtml(username), escapeHtml(newEmail));
    }

    private String buildSecurityAlertHtml(String username, String title, String message, String footer) {
        return """
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto">
                  <h2 style="color:#c0392b">%s</h2>
                  <p>Olá, <strong>%s</strong>!</p>
                  <p>%s</p>
                  <p style="color:#666;font-size:.85rem">%s</p>
                </div>
                """.formatted(escapeHtml(title), escapeHtml(username), escapeHtml(message), escapeHtml(footer));
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
