package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * As quatro permissões de {@code /orders} são separadas porque as operações têm consequências
 * diferentes: ler é inócuo, avançar estágio é expedição, cancelar <b>libera reserva de
 * estoque</b> e reembolsar <b>estorna pagamento, cashback e devolve mercadoria</b>. Estes testes
 * provam que uma não vale pela outra.
 */
@SpringBootTest
@ActiveProfiles("dev")
public class OrdersControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    // Os três campos abaixo só existem para montar o pedido PRÉ-pagamento de
    // cancel_with_order_cancel_returns_200 — ver o comentário naquele teste sobre por que ele não
    // nasce via HTTP como o resto da fixture.
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EstoqueUseCase estoqueUseCase;

    @Autowired
    private CrmUseCase crmUseCase;

    private MockMvc mockMvc;

    private final ObjectMapper om = new ObjectMapper();

    private static final String STATUS_BODY = "{\"status\":\"SEPARADO\"}";
    private static final String CANCEL_BODY = "{\"reason\":\"cliente desistiu\"}";
    private static final String REFUND_BODY = "{\"reason\":\"devolução no prazo\"}";

    /**
     * Preço exato do produto que {@link #givenStockedProduct()} cadastra — o pagamento em
     * {@link #givenConcludedOrder()} precisa bater com ele centavo a centavo (PDV-F006).
     */
    private static final String PRODUCT_PRICE = "100.00";

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ── Fixtures de pedido real, para os testes de sucesso (200) abaixo ─────────────────────────

    /** Depósito + produto precificado + saldo em estoque, com código/SKU únicos por chamada. */
    private record StockedProduct(String suffix, String warehouseCode, String sku) {
    }

    private StockedProduct givenStockedProduct() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String warehouseCode = "LOJA_ORD_SEC_" + suffix;
        String sku = "SKU_ORD_SEC_" + suffix;

        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + warehouseCode + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente-estoque-sec").authorities(
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto Teste\",\"category\":\"testes\","
                        + "\"pricing\":{\"costPrice\":50.00,\"salePrice\":" + PRODUCT_PRICE + "}}")
                .with(user("gerente-estoque-sec").authorities(
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_PRICE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"warehouseCode\":\"" + warehouseCode + "\",\"type\":\"ENTRADA\","
                        + "\"quantity\":10,\"reason\":\"carga teste\"}")
                .with(user("gerente-estoque-sec").authorities(new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isCreated());

        return new StockedProduct(suffix, warehouseCode, sku);
    }

    /**
     * Fecha uma venda de balcão de ponta a ponta via HTTP (PDV-F001/F004/F006): abre sessão de
     * caixa e registra a venda com pagamento em DINHEIRO cobrindo exatamente
     * {@link #PRODUCT_PRICE}. Devolve o id do pedido resultante.
     *
     * <p>Uma venda de balcão nasce {@code CRIADO} e conclui na mesma transação — não existe estado
     * intermediário observável via HTTP (comentário de {@code CRIADO} em {@code OrderStatus.java}).
     * O pedido devolvido aqui já está em {@code CONCLUIDO}, nunca em {@code PAGO}: PAGO é caminho
     * exclusivo do marketplace, confirmado assincronamente pelo webhook de pagamento
     * ({@code Order.paid}, disparado por {@code PaymentWebhookService}), não pela venda de balcão.</p>
     */
    private Long givenConcludedOrder() throws Exception {
        StockedProduct stocked = givenStockedProduct();

        String sessionBody = mockMvc.perform(post("/pdv/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingAmount\":200.00,\"warehouseCode\":\"" + stocked.warehouseCode() + "\"}")
                .with(user("caixa-sec-" + stocked.suffix())
                        .authorities(new SimpleGrantedAuthority("PDV_SESSION_MANAGE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long sessionId = om.readTree(sessionBody).get("id").asLong();

        String saleBody = mockMvc.perform(post("/pdv/sessions/" + sessionId + "/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"sku\":\"" + stocked.sku() + "\",\"quantity\":1}],"
                        + "\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":" + PRODUCT_PRICE + "}]}")
                .with(user("caixa-sec-" + stocked.suffix())
                        .authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(saleBody).get("id").asLong();
    }

    /** VARCHAR(11) — precisa de um valor numérico, não do sufixo alfanumérico usado alhures. */
    private static String uniqueCpf() {
        return String.valueOf(10_000_000_000L + (System.nanoTime() % 89_999_999_999L));
    }

    // ── GET /orders ──────────────────────────────────────────────────────────────────────────

    @Test
    void list_orders_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_orders_without_order_read_returns_403() throws Exception {
        mockMvc.perform(get("/orders")
                .with(user("bob").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_orders_with_order_read_returns_200() throws Exception {
        mockMvc.perform(get("/orders")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_orders_accepts_every_filter() throws Exception {
        mockMvc.perform(get("/orders")
                .param("channel", "MARKETPLACE")
                .param("status", "PAGO")
                .param("customerId", "42")
                .param("from", "2026-07-01T00:00:00Z")
                .param("to", "2026-07-31T23:59:59Z")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_orders_rejects_invalid_channel() throws Exception {
        mockMvc.perform(get("/orders")
                .param("channel", "TELEPATIA")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_orders_rejects_size_above_limit() throws Exception {
        mockMvc.perform(get("/orders")
                .param("size", "101")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isBadRequest());
    }

    // ── GET /orders/{id} ─────────────────────────────────────────────────────────────────────

    @Test
    void get_order_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/orders/999999")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_nonexistent_order_returns_404() throws Exception {
        mockMvc.perform(get("/orders/999999")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isNotFound());
    }

    // ── POST /orders/{id}/status ─────────────────────────────────────────────────────────────

    @Test
    void change_status_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/orders/999999/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(STATUS_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void change_status_with_order_read_only_returns_403() throws Exception {
        // Ler não autoriza despachar.
        mockMvc.perform(post("/orders/999999/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(STATUS_BODY)
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void change_status_with_order_fulfill_reaches_the_service() throws Exception {
        mockMvc.perform(post("/orders/999999/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(STATUS_BODY)
                .with(user("expedicao").authorities(new SimpleGrantedAuthority("ORDER_FULFILL"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void change_status_with_order_fulfill_returns_200() throws Exception {
        // SEPARADO → ENVIADO → ENTREGUE é a esteira real que ORDER_FULFILL/changeStatus cobre (ver
        // a description do endpoint em OrdersController) — CONCLUIDO (venda de balcão) não entra
        // nessa esteira, então a fixture parte de um pedido MARKETPLACE já PAGO, do mesmo jeito que
        // cancel_with_order_cancel_returns_200 persiste um pedido pré-pagamento direto: reproduzir
        // via HTTP o checkout + webhook de pagamento (ECM-F004) exigiria conta de cliente de
        // verdade e depósito padrão configurado globalmente, infraestrutura desproporcional para um
        // teste de autorização (mesmo raciocínio documentado ali).
        //
        // REEMBOLSADO não é usado aqui de propósito: embora OrderStatus.ALLOWED permita a transição
        // a partir de CONCLUIDO/PAGO/etc., Order.withStatus (o método que changeStatus chama) nunca
        // carimba refundedAt — só Order.refunded faz isso, e só o endpoint dedicado /refund
        // (ORDER_REFUND) chama refunded. Ou seja, REEMBOLSADO por aqui sempre falha na invariante do
        // construtor compacto do Order (status REEMBOLSADO exige refundedAt não nulo), então não é
        // uma transição de verdade disponível por este endpoint — achado à parte, fora do escopo
        // deste teste de autorização.
        StockedProduct stocked = givenStockedProduct();
        Customer customer = crmUseCase.createCustomer("Cliente Sec " + stocked.suffix(),
                "1199" + stocked.suffix(), null, uniqueCpf(), "marketplace");
        OrderItem item = OrderItem.fromCatalog(stocked.sku(), BigDecimal.ONE,
                estoqueUseCase.findPricingBySku(stocked.sku()), null);
        Order pending = Order.openMarketplace(customer.id(), stocked.warehouseCode(), List.of(item));
        Order paid = orderRepository.save(pending.paid(Instant.now()));

        mockMvc.perform(post("/orders/" + paid.id() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SEPARADO\"}")
                .with(user("expedicao").authorities(new SimpleGrantedAuthority("ORDER_FULFILL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEPARADO"));
    }

    // ── POST /orders/{id}/cancel ─────────────────────────────────────────────────────────────

    @Test
    void cancel_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/orders/999999/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CANCEL_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancel_with_order_fulfill_returns_403() throws Exception {
        // Despachar não autoriza cancelar: o cancelamento devolve mercadoria ao estoque.
        mockMvc.perform(post("/orders/999999/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CANCEL_BODY)
                .with(user("expedicao").authorities(new SimpleGrantedAuthority("ORDER_FULFILL"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_with_order_cancel_reaches_the_service() throws Exception {
        mockMvc.perform(post("/orders/999999/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CANCEL_BODY)
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_CANCEL"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_with_order_cancel_returns_200() throws Exception {
        // cancelOrder só aceita pedido PRÉ-pagamento (CRIADO/AGUARDANDO_PAGAMENTO — Order.cancelled +
        // OrderStatus.ALLOWED). Venda de balcão nunca fica pré-pagamento: nasce e conclui na mesma
        // transação, então não há endpoint HTTP que devolva um pedido nesse estado (ver
        // givenConcludedOrder). O único jeito de um pedido ficar pré-pagamento é o checkout de
        // marketplace (POST /shop/checkout), que por sua vez exige uma conta de cliente de verdade
        // (User persistido, resolvido por username — não um principal fabricado por
        // SecurityMockMvcRequestPostProcessors) e o depósito padrão do marketplace configurado
        // globalmente (chave estoque.warehouse.default-code) — infraestrutura desproporcional para
        // um teste de autorização, e que o próprio ShopAccountControllerSecurityTest evita pelo
        // mesmo motivo (ver o teste checkout_with_shop_order_own_reaches_the_service ali, que também
        // para em 404 em vez de montar uma conta completa).
        //
        // Por isso o pedido pré-pagamento é persistido diretamente, do mesmo jeito que
        // OrderRefundIT.cancelOrder_onAPrePaymentMarketplaceOrderTouchesNeitherPaymentNorCashback já
        // faz: só o depósito e o produto nascem via HTTP (givenStockedProduct), o suficiente para
        // provar que /cancel executa uma mudança de estado real, não só um 404 de id inexistente.
        StockedProduct stocked = givenStockedProduct();
        Customer customer = crmUseCase.createCustomer("Cliente Sec " + stocked.suffix(),
                "1199" + stocked.suffix(), null, uniqueCpf(), "marketplace");
        OrderItem item = OrderItem.fromCatalog(stocked.sku(), BigDecimal.ONE,
                estoqueUseCase.findPricingBySku(stocked.sku()), null);
        Order pending = orderRepository.save(
                Order.openMarketplace(customer.id(), stocked.warehouseCode(), List.of(item)));

        mockMvc.perform(post("/orders/" + pending.id() + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CANCEL_BODY)
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_CANCEL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
    }

    @Test
    void cancel_without_reason_returns_400() throws Exception {
        // Estorno sem justificativa registrada é indistinguível de erro.
        mockMvc.perform(post("/orders/999999/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_CANCEL"))))
                .andExpect(status().isBadRequest());
    }

    // ── POST /orders/{id}/refund ─────────────────────────────────────────────────────────────

    @Test
    void refund_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/orders/999999/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REFUND_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refund_with_order_cancel_returns_403() throws Exception {
        // Cancelar não autoriza reembolsar: reembolso estorna dinheiro de verdade.
        mockMvc.perform(post("/orders/999999/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REFUND_BODY)
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_CANCEL"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void refund_with_order_refund_reaches_the_service() throws Exception {
        mockMvc.perform(post("/orders/999999/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REFUND_BODY)
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_REFUND"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void refund_with_order_refund_returns_200() throws Exception {
        // refundOrder só aceita pedido PÓS-pagamento (PAGO em diante — Order.refunded +
        // OrderStatus.ALLOWED) e vai direto para REEMBOLSADO, sem passar por changeStatus antes
        // (confirmado em OrderRefundIT: refundOrder é chamado direto sobre o pedido recém-concluído).
        // CONCLUIDO está nesse conjunto — "Terminal, exceto por reembolso" (OrderStatus.java) — então
        // a mesma receita de givenConcludedOrder já serve. Pedido dedicado, próprio desta chamada —
        // givenConcludedOrder gera depósito/SKU/sessão com sufixo único a cada invocação.
        Long orderId = givenConcludedOrder();

        mockMvc.perform(post("/orders/" + orderId + "/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REFUND_BODY)
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_REFUND"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REEMBOLSADO"));
    }

    @Test
    void refund_without_reason_returns_400() throws Exception {
        // Estorno sem justificativa registrada é indistinguível de erro.
        mockMvc.perform(post("/orders/999999/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_REFUND"))))
                .andExpect(status().isBadRequest());
    }
}
