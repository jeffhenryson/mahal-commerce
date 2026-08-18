package com.cernecommerce.adapter.in.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.adapter.in.converter.CashRegisterDTOConverter;
import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class PdvControllerTest {

    private MockMvc mockMvc;
    private PdvUseCase pdvUseCase;
    
    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("operador", null, List.of());

    @BeforeEach
    void setup() {
        pdvUseCase = mock(PdvUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PdvController(pdvUseCase, new OrderDTOConverter(),
                        new CashRegisterDTOConverter(), publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listSessions_returns_200() throws Exception {
        when(pdvUseCase.listSessions(0, 20)).thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/pdv/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void openSession_returns_201() throws Exception {
        CashRegisterSession session = CashRegisterSession.of(1L, "operador", Instant.now(), BigDecimal.TEN, "W1",
                null, null, null, null, null, CashRegisterSession.Status.OPEN);
        when(pdvUseCase.openSession("operador", new BigDecimal("10.00"), "W1")).thenReturn(session);

        mockMvc.perform(post("/pdv/sessions")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingAmount\":10.00,\"warehouseCode\":\"W1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getCurrentSession_returns_200() throws Exception {
        CashRegisterSession session = CashRegisterSession.of(1L, "operador", Instant.now(), BigDecimal.TEN, "W1",
                null, null, null, null, null, CashRegisterSession.Status.OPEN);
        when(pdvUseCase.getCurrentSession("operador")).thenReturn(session);

        mockMvc.perform(get("/pdv/sessions/current").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // PDV-F008 — reserva para retirada

    private static Order reservedOrder() {
        return Order.openBalcao(1L, "LOJA-01", null, List.of(
                        com.cernecommerce.core.domain.model.pedido.OrderItem.fromCatalog("CARV-001",
                                new BigDecimal("2.000"),
                                com.cernecommerce.core.domain.model.estoque.Pricing.of(
                                        new BigDecimal("18.00"), null, new BigDecimal("22.00")), null)))
                .reserved("000001000", null, Instant.now());
    }

    @Test
    void registerSale_reserveForPickupTrue_repassaAoUseCaseEDevolveReservado() throws Exception {
        when(pdvUseCase.registerSale(eq(1L), any(), any(), any(), anyString(), eq(true)))
                .thenReturn(reservedOrder());
        when(pdvUseCase.getOrderPayments(any())).thenReturn(List.of());

        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"DINHEIRO","amount":44.00}],
                                 "reserveForPickup":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVADO"))
                .andExpect(jsonPath("$.reservedAt").exists());

        verify(pdvUseCase).registerSale(eq(1L), any(), any(), any(), anyString(), eq(true));
    }

    @Test
    void registerSale_reserveForPickupAusente_repassaFalse() throws Exception {
        Order concluido = Order.openBalcao(1L, "LOJA-01", null, List.of(
                        com.cernecommerce.core.domain.model.pedido.OrderItem.fromCatalog("CARV-001",
                                new BigDecimal("2.000"),
                                com.cernecommerce.core.domain.model.estoque.Pricing.of(
                                        new BigDecimal("18.00"), null, new BigDecimal("22.00")), null)))
                .concluded("000001000", null, Instant.now());
        when(pdvUseCase.registerSale(eq(1L), any(), any(), any(), anyString(), eq(false)))
                .thenReturn(concluido);
        when(pdvUseCase.getOrderPayments(any())).thenReturn(List.of());

        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"DINHEIRO","amount":44.00}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        verify(pdvUseCase).registerSale(eq(1L), any(), any(), any(), anyString(), eq(false));
    }
}
