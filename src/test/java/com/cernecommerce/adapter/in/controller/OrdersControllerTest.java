package com.cernecommerce.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.dtos.response.OrderAdminResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

public class OrdersControllerTest {

    private MockMvc mockMvc;
    private OrderUseCase orderUseCase;
    private OrderDTOConverter orderConverter;
    private ApplicationEventPublisher publisher;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        orderUseCase = mock(OrderUseCase.class);
        orderConverter = mock(OrderDTOConverter.class);
        publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrdersController(orderUseCase, orderConverter, publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listOrders_returns_200() throws Exception {
        PageResult<Order> orderPage = new PageResult<>(List.of(), 0, 20, 0L, 0);
        when(orderUseCase.listOrders(any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(orderPage);
        
        PageResult<OrderAdminResponseDTO> dtoPage = new PageResult<>(List.of(), 0, 20, 0L, 0);
        when(orderConverter.toAdminResponse(orderPage)).thenReturn(dtoPage);

        mockMvc.perform(get("/orders")
                        .principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getOrder_returns_200() throws Exception {
        Order mockOrder = mock(Order.class);
        when(orderUseCase.getOrder(1L)).thenReturn(mockOrder);

        OrderAdminResponseDTO mockResponse = mock(OrderAdminResponseDTO.class);
        when(orderConverter.toAdminResponse(mockOrder)).thenReturn(mockResponse);

        mockMvc.perform(get("/orders/1")
                        .principal(AUTH))
                .andExpect(status().isOk());
    }

    @Test
    void changeStatus_returns_200() throws Exception {
        Order before = mock(Order.class);
        when(before.status()).thenReturn(OrderStatus.SEPARADO);
        when(orderUseCase.getOrder(1L)).thenReturn(before);
        
        Order after = mock(Order.class);
        when(after.status()).thenReturn(OrderStatus.ENVIADO);
        when(orderUseCase.changeStatus(1L, OrderStatus.ENVIADO, "admin")).thenReturn(after);

        OrderAdminResponseDTO mockResponse = mock(OrderAdminResponseDTO.class);
        when(orderConverter.toAdminResponse(after)).thenReturn(mockResponse);

        mockMvc.perform(post("/orders/1/status")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENVIADO\"}"))
                .andExpect(status().isOk());

        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void cancelOrder_returns_200() throws Exception {
        Order before = mock(Order.class);
        when(before.status()).thenReturn(OrderStatus.CRIADO);
        when(orderUseCase.getOrder(1L)).thenReturn(before);
        
        Order after = mock(Order.class);
        when(after.status()).thenReturn(OrderStatus.CANCELADO);
        when(orderUseCase.cancelOrder(1L, "Motivo", "admin")).thenReturn(after);

        OrderAdminResponseDTO mockResponse = mock(OrderAdminResponseDTO.class);
        when(orderConverter.toAdminResponse(after)).thenReturn(mockResponse);

        mockMvc.perform(post("/orders/1/cancel")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Motivo\"}"))
                .andExpect(status().isOk());

        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void refundOrder_returns_200() throws Exception {
        Order before = mock(Order.class);
        when(before.status()).thenReturn(OrderStatus.PAGO);
        when(orderUseCase.getOrder(1L)).thenReturn(before);
        
        Order after = mock(Order.class);
        when(after.status()).thenReturn(OrderStatus.REEMBOLSADO);
        when(orderUseCase.refundOrder(eq(1L), eq("Motivo"), eq("admin"), anyList())).thenReturn(after);

        OrderAdminResponseDTO mockResponse = mock(OrderAdminResponseDTO.class);
        when(orderConverter.toAdminResponse(after)).thenReturn(mockResponse);

        mockMvc.perform(post("/orders/1/refund")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Motivo\"}"))
                .andExpect(status().isOk());

        verify(publisher).publishEvent(any(Object.class));
    }
}
