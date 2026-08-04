package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

/**
 * ECM-F003 (Fatia 9): {@code SHOP_CART_OWN}/{@code SHOP_ORDER_OWN} provam só que o principal é
 * <b>um</b> cliente — a propriedade do carrinho/pedido é sempre resolvida dentro de
 * {@code ShopService} a partir do username autenticado, nunca de um {@code customerId} do
 * request. Um principal fabricado (sem {@code User} real no banco) chega até o service e recebe
 * 404 {@code USER_NOT_FOUND} — o mesmo "chegou até a lógica de negócio" que
 * {@link OrdersControllerSecurityTest} prova usando um {@code orderId} inexistente, sem precisar
 * de um cliente de verdade cadastrado para o teste.
 */
@SpringBootTest
@ActiveProfiles("dev")
class ShopAccountControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private static final String CANCEL_BODY = "{\"reason\":\"desisti da compra\"}";

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void get_cart_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/shop/cart")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_cart_without_shop_cart_own_returns_403() throws Exception {
        mockMvc.perform(get("/shop/cart")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_cart_with_shop_cart_own_reaches_the_service() throws Exception {
        mockMvc.perform(get("/shop/cart")
                .with(user("cliente-fake").authorities(new SimpleGrantedAuthority("SHOP_CART_OWN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void upsert_cart_item_without_shop_cart_own_returns_403() throws Exception {
        mockMvc.perform(put("/shop/cart/items/ESS-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void upsert_cart_item_without_quantity_returns_400() throws Exception {
        mockMvc.perform(put("/shop/cart/items/ESS-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("cliente-fake").authorities(new SimpleGrantedAuthority("SHOP_CART_OWN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void remove_cart_item_without_shop_cart_own_returns_403() throws Exception {
        mockMvc.perform(delete("/shop/cart/items/ESS-001")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void remove_cart_item_with_shop_cart_own_reaches_the_service() throws Exception {
        mockMvc.perform(delete("/shop/cart/items/ESS-001")
                .with(user("cliente-fake").authorities(new SimpleGrantedAuthority("SHOP_CART_OWN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void checkout_without_shop_order_own_returns_403() throws Exception {
        // SHOP_CART_OWN não autoriza fechar pedido — carrinho e checkout são permissões separadas.
        mockMvc.perform(post("/shop/checkout")
                .with(user("bob").authorities(new SimpleGrantedAuthority("SHOP_CART_OWN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkout_with_shop_order_own_reaches_the_service() throws Exception {
        mockMvc.perform(post("/shop/checkout")
                .with(user("cliente-fake").authorities(new SimpleGrantedAuthority("SHOP_ORDER_OWN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void list_my_orders_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/shop/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_my_orders_without_shop_order_own_returns_403() throws Exception {
        mockMvc.perform(get("/shop/orders")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_my_order_without_shop_order_own_returns_403() throws Exception {
        mockMvc.perform(get("/shop/orders/1")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_my_order_with_shop_order_own_reaches_the_service() throws Exception {
        mockMvc.perform(get("/shop/orders/1")
                .with(user("cliente-fake").authorities(new SimpleGrantedAuthority("SHOP_ORDER_OWN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void cancel_my_order_without_shop_order_own_returns_403() throws Exception {
        mockMvc.perform(post("/shop/orders/1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CANCEL_BODY)
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_my_order_without_reason_returns_400() throws Exception {
        // Mesma exigência de OrdersController: estorno sem justificativa é indistinguível de erro.
        mockMvc.perform(post("/shop/orders/1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("cliente-fake").authorities(new SimpleGrantedAuthority("SHOP_ORDER_OWN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancel_my_order_with_shop_order_own_reaches_the_service() throws Exception {
        mockMvc.perform(post("/shop/orders/1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CANCEL_BODY)
                .with(user("cliente-fake").authorities(new SimpleGrantedAuthority("SHOP_ORDER_OWN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }
}
