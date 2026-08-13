package com.cernecommerce.adapter.out.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ThymeleafEmailRendererTest {

    private ThymeleafEmailRenderer renderer;

    @BeforeEach
    void setup() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        renderer = new ThymeleafEmailRenderer(engine);
    }

    @Test
    void verification_code_template_renderiza_com_campos_corretos() {
        String html = renderer.render("verification-code", Map.of(
                "username", "alice",
                "code", "ABC123",
                "verifyUrl", "http://localhost/verify?code=ABC123",
                "ttlMinutes", 15L));

        assertThat(html).contains("alice");
        assertThat(html).contains("ABC123");
        assertThat(html).contains("http://localhost/verify?code=ABC123");
        assertThat(html).contains("15");
    }

    @Test
    void password_reset_template_renderiza_com_campos_corretos() {
        String html = renderer.render("password-reset", Map.of(
                "username", "bob",
                "resetLink", "http://localhost/reset?token=xyz"));

        assertThat(html).contains("bob");
        assertThat(html).contains("http://localhost/reset?token=xyz");
    }

    @Test
    void email_change_template_renderiza_com_campos_corretos() {
        String html = renderer.render("email-change", Map.of(
                "username", "carol",
                "newEmail", "carol.new@example.com"));

        assertThat(html).contains("carol");
        assertThat(html).contains("carol.new@example.com");
    }

    @Test
    void security_alert_template_renderiza_com_campos_corretos() {
        String html = renderer.render("security-alert", Map.of(
                "username", "dave",
                "title", "Conta bloqueada",
                "message", "Múltiplas tentativas detectadas.",
                "footerMessage", "Entre em contato com o suporte."));

        assertThat(html).contains("dave");
        assertThat(html).contains("Conta bloqueada");
        assertThat(html).contains("Múltiplas tentativas detectadas.");
        assertThat(html).contains("Entre em contato com o suporte.");
    }

    @Test
    void security_alert_template_escapa_html_em_valores_maliciosos() {
        String html = renderer.render("security-alert", Map.of(
                "username", "<script>alert('xss')</script>",
                "title", "Test",
                "message", "Test",
                "footerMessage", "Test"));

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void role_changed_template_renderiza_com_campos_corretos() {
        String html = renderer.render("role-changed", Map.of(
                "username", "eve",
                "title", "Papel atribuído",
                "message", "O papel ROLE_ADMIN foi atribuído à sua conta."));

        assertThat(html).contains("eve");
        assertThat(html).contains("Papel atribuído");
        assertThat(html).contains("ROLE_ADMIN");
    }

    @Test
    void order_confirmation_template_renderiza_com_campos_corretos() {
        String html = renderer.render("order-confirmation", Map.of(
                "customerName", "Maria",
                "orderReference", "Pedido #7",
                "total", new BigDecimal("99.90"),
                "itemCount", 3,
                "checkoutUrl", "http://checkout-url"));

        assertThat(html).contains("Maria");
        assertThat(html).contains("Pedido #7");
        assertThat(html).contains("99.90");
        assertThat(html).contains("http://checkout-url");
    }

    @Test
    void order_status_update_template_renderiza_com_campos_corretos() {
        String html = renderer.render("order-status-update", Map.of(
                "customerName", "Maria",
                "orderReference", "Pedido #7",
                "newStatusLabel", "Pagamento confirmado"));

        assertThat(html).contains("Maria");
        assertThat(html).contains("Pedido #7");
        assertThat(html).contains("Pagamento confirmado");
    }

    @Test
    void order_cancellation_template_usa_texto_de_reembolso_quando_refunded() {
        String html = renderer.render("order-cancellation", Map.of(
                "customerName", "Maria",
                "orderReference", "Pedido #7",
                "reason", "Produto com defeito",
                "refunded", true));

        assertThat(html).contains("Maria");
        assertThat(html).contains("Pedido #7");
        assertThat(html).contains("reembolsado");
        assertThat(html).contains("Produto com defeito");
    }

    @Test
    void order_cancellation_template_usa_texto_de_cancelamento_quando_nao_refunded() {
        String html = renderer.render("order-cancellation", Map.of(
                "customerName", "Maria",
                "orderReference", "Pedido #7",
                "reason", "Cliente desistiu",
                "refunded", false));

        assertThat(html).contains("cancelado");
        assertThat(html).doesNotContain("reembolsado");
    }
}
