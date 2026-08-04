package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
 * PLAT-C034 / Fatia 8: {@code /shop/register} é a exceção deliberada — diferente de todo outro
 * controller de negócio (ver {@link EcommerceControllerSecurityTest}, que exige autenticação),
 * este precisa responder sem token nenhum.
 */
@SpringBootTest
@ActiveProfiles("dev")
class ShopControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void register_withoutAnyAuthentication_isNotBlockedBySecurity() throws Exception {
        String email = "shop.security." + System.nanoTime() + "@test.com";

        mockMvc.perform(post("/shop/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cliente Teste\",\"email\":\"" + email + "\",\"password\":\"S3nha@forte\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));
    }

    /**
     * ECM-F002: nenhum depósito padrão é semeado em nenhum profile (criar depósito é ação do
     * operador, não algo que a aplicação semeie sozinha — ver V77 e {@code EstoqueService
     * #getDefaultWarehouse}), então este contexto de teste devolve 503 de forma determinística.
     * O que importa aqui não é o 503 em si, e sim que a requisição chegou até o service sem ser
     * barrada em 401/403 pela cadeia de segurança — a mesma garantia de {@code register_*} acima.
     */
    @Test
    void catalog_withoutAnyAuthentication_isNotBlockedBySecurity() throws Exception {
        mockMvc.perform(get("/shop/catalog"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("DEFAULT_WAREHOUSE_NOT_CONFIGURED"));
    }

    @Test
    void catalogItem_withoutAnyAuthentication_isNotBlockedBySecurity() throws Exception {
        mockMvc.perform(get("/shop/catalog/ANY-SKU"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }
}
