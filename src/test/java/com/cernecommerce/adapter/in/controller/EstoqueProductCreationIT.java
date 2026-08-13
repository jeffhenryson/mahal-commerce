package com.cernecommerce.adapter.in.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /estoque/products} com estoque inicial e/ou {@code type} explícito (EST-F023), de
 * ponta a ponta contra banco real: prova a atomicidade (produto e saldo na mesma transação, ou
 * nenhum dos dois) e as invariantes de kit (sem grade, sem estoque inicial próprio).
 */
@SpringBootTest
@ActiveProfiles("dev")
class EstoqueProductCreationIT {

    private static final SimpleGrantedAuthority ROLE_ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final SimpleGrantedAuthority PRODUCT_MANAGE = new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE");
    private static final SimpleGrantedAuthority STOCK_MANAGE = new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE");
    private static final SimpleGrantedAuthority WAREHOUSE_MANAGE = new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE");
    private static final SimpleGrantedAuthority WAREHOUSE_READ = new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void createWarehouse(MockMvc mvc, String code) throws Exception {
        mvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Deposito Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(ROLE_ADMIN, WAREHOUSE_MANAGE)))
                .andExpect(status().isCreated());
    }

    @Test
    void createProduct_comEstoqueInicial_criaProdutoESaldoNaMesmaTransacao() throws Exception {
        MockMvc mvc = mvc();
        long nonce = System.nanoTime();
        String code = "WH_INIT_" + nonce;
        String sku = "INIT_OK_" + nonce;
        createWarehouse(mvc, code);

        mvc.perform(post("/estoque/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto\",\"category\":\"testes\","
                                + "\"initialStock\":{\"warehouseCode\":\"" + code + "\",\"quantity\":15}}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE, STOCK_MANAGE)))
                .andExpect(status().isCreated());

        mvc.perform(get("/estoque/stock-balance").param("sku", sku).param("warehouseCode", code)
                        .with(user("gerente").authorities(ROLE_ADMIN, WAREHOUSE_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(15.0));

        mvc.perform(get("/estoque/movements").param("sku", sku).param("warehouseCode", code)
                        .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("ENTRADA"))
                .andExpect(jsonPath("$.content[0].username").value("gerente"));
    }

    /**
     * Prova a atomicidade pelo lado do rollback: depósito inexistente faz {@code adjustStock}
     * lançar 404 — e a criação do produto, que já tinha rodado antes na mesma transação, precisa
     * desfazer junto. Sem a transação única, o produto ficaria criado com saldo zero e sem forma
     * de desfazer do lado do cliente — exatamente o problema que EST-F023 resolve.
     */
    @Test
    void createProduct_comDepositoInexistente_naoDeixaProdutoOrfaoPraTras() throws Exception {
        MockMvc mvc = mvc();
        long nonce = System.nanoTime();
        String sku = "INIT_ROLLBACK_" + nonce;

        mvc.perform(post("/estoque/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto\",\"category\":\"testes\","
                                + "\"initialStock\":{\"warehouseCode\":\"DEPOSITO_FANTASMA_" + nonce
                                + "\",\"quantity\":10}}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE, STOCK_MANAGE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));

        // O rollback desfez a criação do produto — não fica órfão com saldo zero.
        mvc.perform(get("/estoque/products/" + sku)
                        .with(user("gerente").authorities(ROLE_ADMIN, new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProduct_comEstoqueInicialSemEstoqueStockManage_returns_403() throws Exception {
        MockMvc mvc = mvc();
        long nonce = System.nanoTime();
        String code = "WH_PERM_" + nonce;
        String sku = "INIT_PERM_" + nonce;
        createWarehouse(mvc, code);

        mvc.perform(post("/estoque/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto\",\"category\":\"testes\","
                                + "\"initialStock\":{\"warehouseCode\":\"" + code + "\",\"quantity\":5}}")
                        // Só ESTOQUE_PRODUCT_MANAGE — sem ESTOQUE_STOCK_MANAGE.
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_semEstoqueInicial_naoExigeEstoqueStockManage() throws Exception {
        MockMvc mvc = mvc();
        String sku = "INIT_NONE_" + System.nanoTime();

        // Continua funcionando só com ESTOQUE_PRODUCT_MANAGE quando não há initialStock —
        // touchesStock() não pode virar exigência incondicional.
        mvc().perform(post("/estoque/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto\",\"category\":\"testes\"}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isCreated());
    }

    @Test
    void createProduct_tipoKit_criaProdutoJaComoKit() throws Exception {
        String sku = "KIT_DIRETO_" + System.nanoTime();

        mvc().perform(post("/estoque/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Kit\",\"category\":\"combo\",\"type\":\"KIT\"}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("KIT"));
    }

    @Test
    void createProduct_tipoKitComVariants_returns_409() throws Exception {
        String sku = "KIT_VAR_" + System.nanoTime();

        mvc().perform(post("/estoque/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Kit\",\"category\":\"combo\",\"type\":\"KIT\","
                                + "\"variants\":[{\"sku\":\"" + sku + "-A\"}]}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("KIT_HAS_VARIANTS"));
    }

    @Test
    void createProduct_tipoKitComEstoqueInicial_returns_409() throws Exception {
        MockMvc mvc = mvc();
        long nonce = System.nanoTime();
        String code = "WH_KITSTOCK_" + nonce;
        String sku = "KIT_STOCK_" + nonce;
        createWarehouse(mvc, code);

        mvc.perform(post("/estoque/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Kit\",\"category\":\"combo\",\"type\":\"KIT\","
                                + "\"initialStock\":{\"warehouseCode\":\"" + code + "\",\"quantity\":3}}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE, STOCK_MANAGE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("KIT_INITIAL_STOCK_NOT_ALLOWED"));
    }
}
