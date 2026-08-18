package com.cernecommerce.adapter.out.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Bean do webhook de automações do CRM — sem {@code baseUrl} fixa, já que a URL é dinâmica
 * (configurada por automação). Mesmo molde de timeout explícito de
 * {@code PaymentGatewayAdapterConfig}.
 */
@Configuration
class CrmWebhookConfig {

    @Bean
    CampaignWebhookAdapter campaignWebhookAdapter(
            @Value("${crm.webhook.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${crm.webhook.read-timeout-ms:5000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        return new CampaignWebhookAdapter(restClient);
    }
}
