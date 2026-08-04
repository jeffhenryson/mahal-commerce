package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * ECM-F004 (Fatia 10): {@code /webhooks/payments/{provider}} é a exceção deliberada, no mesmo
 * molde de {@link ShopControllerSecurityTest} — é o gateway de pagamento chamando, sem sessão de
 * usuário nenhuma. Sem pedido {@code PENDING} nenhum cadastrado neste contexto de teste, a
 * notificação é um no-op determinístico — o que importa provar aqui não é o resultado de negócio,
 * e sim que a requisição chegou até o service sem ser barrada em 401/403 pela cadeia de segurança.
 */
@SpringBootTest
@ActiveProfiles("dev")
class PaymentWebhookControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void receive_withoutAnyAuthentication_isNotBlockedBySecurity() throws Exception {
        mockMvc.perform(post("/webhooks/payments/infinitepay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"order_nsu\":\"999999999\",\"transaction_nsu\":\"txn-1\",\"invoice_slug\":\"slug-1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void receive_withMalformedBody_stillNotBlockedBySecurity() throws Exception {
        // Corpo ilegível vira 400 pelo handler genérico de UNREADABLE_BODY, não 401/403 — prova
        // que a rota é mesmo pública antes de qualquer validação de payload.
        mockMvc.perform(post("/webhooks/payments/infinitepay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not-json"))
                .andExpect(status().isBadRequest());
    }
}
