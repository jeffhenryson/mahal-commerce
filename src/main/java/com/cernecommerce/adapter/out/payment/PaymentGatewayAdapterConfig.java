package com.cernecommerce.adapter.out.payment;

import com.cernecommerce.core.ports.out.ecommerce.PaymentGatewayPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Seleciona o adapter de gateway de pagamento conforme {@code payment.gateway.provider}
 * (ECM-F004) — mesmo molde de {@code EmailAdapterConfig}:
 * <ul>
 *   <li>{@code infinitepay} — chama a API de verdade (requer {@code infinitepay.handle} real).</li>
 *   <li>Qualquer outro valor / ausente — {@link StubPaymentGatewayAdapter}, padrão dev/testes.</li>
 * </ul>
 * {@code @Bean} em {@code @Configuration}, não {@code @Component}: garante que
 * {@code @ConditionalOnMissingBean} seja avaliado na ordem correta.
 */
@Configuration
class PaymentGatewayAdapterConfig {

    @Bean
    @ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "infinitepay")
    InfinitePayAdapter infinitePayAdapter(
            @Value("${infinitepay.handle}") String handle,
            @Value("${infinitepay.api-url:https://api.checkout.infinitepay.io}") String apiUrl,
            @Value("${infinitepay.redirect-url}") String redirectUrl,
            @Value("${infinitepay.webhook-url}") String webhookUrl) {
        // A chamada de criação do link acontece dentro da transação de checkout (ShopService) —
        // um timeout curto e explícito evita segurar a transação presa a um gateway lento.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(requestFactory)
                .build();
        return new InfinitePayAdapter(restClient, handle, redirectUrl, webhookUrl);
    }

    @Bean
    @ConditionalOnMissingBean(PaymentGatewayPort.class)
    StubPaymentGatewayAdapter stubPaymentGatewayAdapter() {
        return new StubPaymentGatewayAdapter();
    }
}
