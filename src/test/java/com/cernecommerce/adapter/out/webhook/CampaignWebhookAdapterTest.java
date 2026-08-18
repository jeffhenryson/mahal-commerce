package com.cernecommerce.adapter.out.webhook;

import com.cernecommerce.core.domain.model.crm.WebhookDispatchResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CampaignWebhookAdapterTest {

    private static final String URL = "https://n8n.example.com/webhook/abc";

    @Test
    void send_returnsSuccessOn2xx() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CampaignWebhookAdapter adapter = new CampaignWebhookAdapter(builder.build());

        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        WebhookDispatchResult result = adapter.send(URL, Map.of(), Map.of("mensagem", "Ola"));

        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.errorMessage()).isNull();
        server.verify();
    }

    @Test
    void send_returnsFailureOnNon2xxWithoutThrowing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CampaignWebhookAdapter adapter = new CampaignWebhookAdapter(builder.build());

        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("upstream indisponivel"));

        WebhookDispatchResult result = adapter.send(URL, Map.of(), Map.of("mensagem", "Ola"));

        assertThat(result.success()).isFalse();
        assertThat(result.statusCode()).isEqualTo(500);
        assertThat(result.errorMessage()).isNotNull();
        server.verify();
    }

    @Test
    void send_returnsFailureWithoutThrowingWhenUrlIsUnreachable() {
        CampaignWebhookAdapter adapter = new CampaignWebhookAdapter(RestClient.builder().build());

        WebhookDispatchResult result = adapter.send("http://localhost:1", Map.of(), Map.of("mensagem", "Ola"));

        assertThat(result.success()).isFalse();
        assertThat(result.statusCode()).isNull();
        assertThat(result.errorMessage()).isNotNull();
    }
}
