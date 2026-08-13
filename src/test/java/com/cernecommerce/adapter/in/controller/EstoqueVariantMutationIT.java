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
 * {@code POST}/{@code PATCH /estoque/products/{sku}/variants...} (EST-F024), de ponta a ponta
 * contra banco real: prova a propriedade central do design — anexar variação nova e desativar
 * uma existente não apagam nem recriam a linha da variação já cadastrada, então o saldo/histórico
 * dela (referenciado por SKU como texto livre, sem FK) sobrevive intacto.
 */
@SpringBootTest
@ActiveProfiles("dev")
class EstoqueVariantMutationIT {

    private static final SimpleGrantedAuthority ROLE_ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final SimpleGrantedAuthority PRODUCT_MANAGE = new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE");
    private static final SimpleGrantedAuthority PRICE_MANAGE = new SimpleGrantedAuthority("ESTOQUE_PRODUCT_PRICE_MANAGE");
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
    void addVariants_anexaNovaSemMexerNoSaldoDaExistente() throws Exception {
        MockMvc mvc = mvc();
        long nonce = System.nanoTime();
        String code = "WH_GRADE_" + nonce;
        String sku = "GRADE_ADD_" + nonce;
        createWarehouse(mvc, code);

        mvc.perform(post("/estoque/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Essência\",\"category\":\"testes\","
                                + "\"variants\":[{\"sku\":\"" + sku + "-A\"}]}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isCreated());

        mvc.perform(post("/estoque/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "-A\",\"warehouseCode\":\"" + code + "\",\"type\":\"ENTRADA\","
                                + "\"quantity\":8,\"reason\":\"Carga inicial\"}")
                        .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isCreated());

        mvc.perform(post("/estoque/products/" + sku + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[{\"sku\":\"" + sku + "-B\"}]}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants.length()").value(2));

        // Saldo da variação A, criada antes, não foi tocado por anexar B.
        mvc.perform(get("/estoque/stock-balance").param("sku", sku + "-A").param("warehouseCode", code)
                        .with(user("gerente").authorities(ROLE_ADMIN, WAREHOUSE_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(8.0));
    }

    @Test
    void updateVariant_desativarNaoApagaOSaldoExistente() throws Exception {
        MockMvc mvc = mvc();
        long nonce = System.nanoTime();
        String code = "WH_GRADEP_" + nonce;
        String sku = "GRADE_PATCH_" + nonce;
        createWarehouse(mvc, code);

        mvc.perform(post("/estoque/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Essência\",\"category\":\"testes\","
                                + "\"variants\":[{\"sku\":\"" + sku + "-A\"}]}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isCreated());

        mvc.perform(post("/estoque/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "-A\",\"warehouseCode\":\"" + code + "\",\"type\":\"ENTRADA\","
                                + "\"quantity\":6,\"reason\":\"Carga inicial\"}")
                        .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isCreated());

        mvc.perform(patch("/estoque/products/" + sku + "/variants/" + sku + "-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].active").value(false));

        // Desativar não é excluir: o saldo/histórico da variação continua íntegro.
        mvc.perform(get("/estoque/stock-balance").param("sku", sku + "-A").param("warehouseCode", code)
                        .with(user("gerente").authorities(ROLE_ADMIN, WAREHOUSE_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(6.0));

        mvc.perform(get("/estoque/movements").param("sku", sku + "-A").param("warehouseCode", code)
                        .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void addVariants_produtoInexistente_returns_404() throws Exception {
        mvc().perform(post("/estoque/products/SKU_FANTASMA_" + System.nanoTime() + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[{\"sku\":\"NAO-IMPORTA-A\"}]}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void updateVariant_variacaoInexistente_returns_404() throws Exception {
        MockMvc mvc = mvc();
        String sku = "GRADE_404_" + System.nanoTime();
        mvc.perform(post("/estoque/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto\",\"category\":\"testes\"}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isCreated());

        mvc.perform(patch("/estoque/products/" + sku + "/variants/" + sku + "-FANTASMA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_VARIANT_NOT_FOUND"));
    }

    @Test
    void addVariants_comPricingSemPriceManage_returns_403() throws Exception {
        MockMvc mvc = mvc();
        String sku = "GRADE_PERM_" + System.nanoTime();
        mvc.perform(post("/estoque/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto\",\"category\":\"testes\"}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isCreated());

        mvc.perform(post("/estoque/products/" + sku + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[{\"sku\":\"" + sku + "-A\",\"pricing\":{\"salePrice\":9.90}}]}")
                        // Só ESTOQUE_PRODUCT_MANAGE — sem ESTOQUE_PRODUCT_PRICE_MANAGE.
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addVariants_comPricingEPriceManage_persistePrecoProprio() throws Exception {
        MockMvc mvc = mvc();
        String sku = "GRADE_PRICE_" + System.nanoTime();
        mvc.perform(post("/estoque/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto\",\"category\":\"testes\"}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isCreated());

        mvc.perform(post("/estoque/products/" + sku + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[{\"sku\":\"" + sku + "-A\",\"pricing\":{\"salePrice\":9.90}}]}")
                        .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE, PRICE_MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].pricing.salePrice").value(9.90));
    }
}
