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

import java.util.UUID;

/** PDV-F021 — atendente lê o cardápio e lança; só o admin mexe no cadastro. */
@SpringBootTest
@ActiveProfiles("dev")
public class PdvSessaoControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private static final SimpleGrantedAuthority COMANDA = new SimpleGrantedAuthority("PDV_COMANDA_MANAGE");
    private static final SimpleGrantedAuthority SESSAO_MANAGE = new SimpleGrantedAuthority("PDV_SESSAO_MANAGE");

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void menu_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/pdv/sessao/cardapio")).andExpect(status().isUnauthorized());
    }

    @Test
    void menu_with_comanda_manage_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/sessao/cardapio").with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faixas").isArray())
                .andExpect(jsonPath("$.duploRoshHoje").isBoolean());
    }

    @Test
    void manage_tiers_with_only_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/sessao/faixas").with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/pdv/sessao/faixas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"preco\":10}")
                        .with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/pdv/sessao/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeVasoGrandePreco\":10}")
                        .with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_tier_with_sessao_manage_returns_201_and_repeated_name_409() throws Exception {
        String nome = "Faixa " + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"nome\":\"" + nome + "\",\"preco\":30.00,\"marcas\":\"Luk, Smynar, Nay\",\"ordem\":2}";
        mockMvc.perform(post("/pdv/sessao/faixas").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(user("gerente").authorities(SESSAO_MANAGE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preco").value(30.00))
                .andExpect(jsonPath("$.ativo").value(true));
        mockMvc.perform(post("/pdv/sessao/faixas").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(user("gerente").authorities(SESSAO_MANAGE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SESSION_MENU_CONFLICT"));
    }

    @Test
    void add_session_without_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/1/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tierId\":1,\"essencia\":\"Zomo\"}")
                        .with(user("gerente").authorities(SESSAO_MANAGE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void add_session_to_unknown_comanda_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tierId\":1,\"essencia\":\"Zomo\"}")
                        .with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void add_session_without_essence_returns_400() throws Exception {
        mockMvc.perform(post("/pdv/comandas/1/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tierId\":1,\"essencia\":\"\"}")
                        .with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isBadRequest());
    }
}
