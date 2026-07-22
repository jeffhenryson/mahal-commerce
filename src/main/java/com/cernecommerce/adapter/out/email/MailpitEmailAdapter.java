package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

public class MailpitEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(MailpitEmailAdapter.class);
    private final RestClient restClient;
    private final String fromAddress;
    private final long ttlMinutes;
    private final String emailSubject;
    private final String verificationFrontendUrl;
    private final ThymeleafEmailRenderer renderer;
    private final MeterRegistry meterRegistry;

    public MailpitEmailAdapter(
            RestClient restClient,
            String fromAddress,
            long ttlMinutes,
            String emailSubject,
            String verificationFrontendUrl,
            ThymeleafEmailRenderer renderer,
            MeterRegistry meterRegistry) {
        this.restClient = restClient;
        this.fromAddress = fromAddress;
        this.ttlMinutes = ttlMinutes;
        this.emailSubject = emailSubject;
        this.verificationFrontendUrl = verificationFrontendUrl;
        this.renderer = renderer;
        this.meterRegistry = meterRegistry;
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendVerificationCode(String to, String username, String code) {
        String verifyUrl = verificationFrontendUrl + "?code=" + code;
        String html = renderer.render("verification-code", Map.of(
                "username", username,
                "code", code,
                "verifyUrl", verifyUrl,
                "ttlMinutes", ttlMinutes));
        send(to, emailSubject, html, "email.verification");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPasswordResetLink(String to, String username, String resetLink) {
        String html = renderer.render("password-reset", Map.of(
                "username", username,
                "resetLink", resetLink));
        send(to, "Recuperação de senha", html, "email.password-reset");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendEmailChangeNotification(String oldEmail, String username, String newEmail) {
        String html = renderer.render("email-change", Map.of(
                "username", username,
                "newEmail", newEmail));
        send(oldEmail, "Seu email foi alterado", html, "email.email-change-notification");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPasswordChangedAlert(String to, String username) {
        String html = renderer.render("security-alert", Map.of(
                "username", username,
                "title", "Sua senha foi alterada",
                "message", "A senha da sua conta foi alterada agora.",
                "footerMessage", "Se não foi você, entre em contato com o suporte imediatamente e revogue suas sessões."));
        send(to, "Alerta de segurança: senha alterada", html, "email.security-alert.password-changed");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendAccountLockedAlert(String to, String username) {
        String html = renderer.render("security-alert", Map.of(
                "username", username,
                "title", "Conta temporariamente bloqueada",
                "message", "Sua conta foi bloqueada temporariamente devido a múltiplas tentativas de login malsucedidas.",
                "footerMessage", "Aguarde alguns minutos e tente novamente. Se não foi você, sua senha pode estar comprometida."));
        send(to, "Alerta de segurança: conta bloqueada", html, "email.security-alert.account-locked");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendTotpStatusAlert(String to, String username, boolean enabled) {
        String action = enabled ? "ativada" : "desativada";
        String detail = enabled
                ? "A autenticação em dois fatores foi ativada na sua conta."
                : "A autenticação em dois fatores foi desativada na sua conta.";
        String html = renderer.render("security-alert", Map.of(
                "username", username,
                "title", "Autenticação em dois fatores " + action,
                "message", detail,
                "footerMessage", "Se não foi você, acesse sua conta e revogue todas as sessões ativas imediatamente."));
        send(to, "Alerta de segurança: 2FA " + action, html, "email.security-alert.totp-" + (enabled ? "enabled" : "disabled"));
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendTokenTheftAlert(String to, String username) {
        String html = renderer.render("security-alert", Map.of(
                "username", username,
                "title", "Acesso suspeito detectado",
                "message", "Detectamos o reuso de uma credencial de sessão já utilizada — todas as sessões da sua conta foram encerradas automaticamente.",
                "footerMessage", "Se não foi você, sua conta pode estar comprometida. Troque sua senha imediatamente."));
        send(to, "Alerta de segurança: acesso suspeito", html, "email.security-alert.token-theft");
    }

    @Override
    public EmailChannelStatus channelStatus() {
        return EmailChannelStatus.of(true, "MAILPIT", "Conectado ao Mailpit (ambiente de homologação)");
    }

    private void send(String to, String subject, String html, String logPrefix) {
        Map<String, Object> body = Map.of(
                "From", Map.of("Email", fromAddress),
                "To", List.of(Map.of("Email", to)),
                "Subject", subject,
                "HTML", html
        );
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("{}.sent to={} via Mailpit", logPrefix, to);
            meterRegistry.counter("email.sent.total", "type", logPrefix).increment();
        } catch (Exception ex) {
            log.error("{}.failed to={} via Mailpit error={}", logPrefix, to, ex.getMessage());
            meterRegistry.counter("email.failed.total", "type", logPrefix).increment();
            throw new EmailDeliveryException(ex.getMessage());
        }
    }
}
