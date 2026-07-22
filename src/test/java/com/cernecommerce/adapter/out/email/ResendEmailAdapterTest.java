package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ResendEmailAdapterTest {

    private MockRestServiceServer server;
    private ResendEmailAdapter adapter;
    private ThymeleafEmailRenderer renderer;

    private static final String FROM = "noreply@test.com";
    private static final String API_URL = "https://api.resend.com/emails";

    @BeforeEach
    void setup() {
        renderer = mock(ThymeleafEmailRenderer.class);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(API_URL)
                .defaultHeader("Authorization", "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new ResendEmailAdapter(builder.build(), FROM, 15, "Confirme seu cadastro",
                "http://localhost:4200/auth/verify-email", renderer, new SimpleMeterRegistry());
    }

    @Test
    void sendVerificationCode_envia_post_com_campos_corretos() throws Exception {
        when(renderer.render(eq("verification-code"), anyMap())).thenReturn("<html>code</html>");
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value(FROM))
                .andExpect(jsonPath("$.to[0]").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("Confirme seu cadastro"))
                .andExpect(jsonPath("$.html").value("<html>code</html>"))
                .andRespond(withSuccess());

        adapter.sendVerificationCode("user@example.com", "alice", "ABC123");

        server.verify();
        verify(renderer).render(eq("verification-code"), argThat(m ->
                "alice".equals(m.get("username")) && "ABC123".equals(m.get("code"))));
    }

    @Test
    void sendPasswordResetLink_envia_post_com_campos_corretos() throws Exception {
        when(renderer.render(eq("password-reset"), anyMap())).thenReturn("<html>reset</html>");
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.to[0]").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("Recuperação de senha"))
                .andRespond(withSuccess());

        adapter.sendPasswordResetLink("user@example.com", "alice", "http://reset-link");

        server.verify();
        verify(renderer).render(eq("password-reset"), argThat(m ->
                "alice".equals(m.get("username")) && "http://reset-link".equals(m.get("resetLink"))));
    }

    @Test
    void sendVerificationCode_lanca_EmailDeliveryException_quando_api_falha() {
        when(renderer.render(anyString(), anyMap())).thenReturn("<html/>");
        server.expect(requestTo(API_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.sendVerificationCode("user@example.com", "alice", "code"))
                .isInstanceOf(EmailDeliveryException.class);
    }

    @Test
    void sendPasswordChangedAlert_usa_template_security_alert() throws Exception {
        when(renderer.render(eq("security-alert"), anyMap())).thenReturn("<html>alert</html>");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Alerta de segurança: senha alterada"))
                .andRespond(withSuccess());

        adapter.sendPasswordChangedAlert("user@example.com", "alice");

        server.verify();
        verify(renderer).render(eq("security-alert"), argThat((Map<String, Object> m) ->
                "alice".equals(m.get("username"))));
    }

    @Test
    void sendTokenTheftAlert_usa_template_security_alert() throws Exception {
        when(renderer.render(eq("security-alert"), anyMap())).thenReturn("<html>theft</html>");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Alerta de segurança: acesso suspeito"))
                .andRespond(withSuccess());

        adapter.sendTokenTheftAlert("user@example.com", "alice");

        server.verify();
    }

    @Test
    void channelStatus_reportsConnectedToResend() {
        EmailChannelStatus status = adapter.channelStatus();

        assertThat(status.conectado()).isTrue();
        assertThat(status.provedor()).isEqualTo("RESEND");
    }
}
