package com.cernecommerce.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.adapter.in.converter.ProductDTOConverter;
import com.cernecommerce.adapter.in.converter.StockMovementDTOConverter;
import com.cernecommerce.adapter.in.converter.WarehouseDTOConverter;
import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
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

public class EstoqueControllerTest {

    private MockMvc mockMvc;
    private EstoqueUseCase estoqueUseCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        estoqueUseCase = mock(EstoqueUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EstoqueController(estoqueUseCase, new ProductDTOConverter(),
                        new WarehouseDTOConverter(), new StockMovementDTOConverter(), publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Product product(String sku) {
        return Product.of(1L, sku, "Narguile Aladin", "narguile", true,
                List.of(ProductVariant.of(1L, sku + "-M", List.of(new ProductAttribute("sabor", "menta")), true)));
    }

    @Test
    void list_returns_200_with_products() throws Exception {
        when(estoqueUseCase.listProducts(0, 20))
                .thenReturn(new PageResult<>(List.of(product("NARG-001")), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].sku").value("NARG-001"))
                .andExpect(jsonPath("$.content[0].variants[0].attributes[0].type").value("sabor"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void create_returns_201() throws Exception {
        Product created = product("NARG-001");
        when(estoqueUseCase.createProduct(eq("NARG-001"), eq("Narguile Aladin"), eq("narguile"), any()))
                .thenReturn(created);

        String body = "{\"sku\":\"NARG-001\",\"name\":\"Narguile Aladin\",\"category\":\"narguile\","
                + "\"variants\":[{\"sku\":\"NARG-001-M\",\"attributes\":[{\"type\":\"sabor\",\"value\":\"menta\"}]}]}";

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("NARG-001"));
    }

    @Test
    void create_without_sku_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Narguile Aladin\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicate_sku_returns_409() throws Exception {
        when(estoqueUseCase.createProduct(eq("NARG-001"), any(), any(), any()))
                .thenThrow(new DuplicateSkuException("NARG-001"));

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile Aladin\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SKU_ALREADY_EXISTS"));
    }

    @Test
    void create_product_without_variants_returns_201() throws Exception {
        Product created = Product.of(2L, "CARV-001", "Carvão Coco", "carvao", true, List.of());
        when(estoqueUseCase.createProduct(eq("CARV-001"), eq("Carvão Coco"), eq("carvao"), any()))
                .thenReturn(created);

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"CARV-001\",\"name\":\"Carvão Coco\",\"category\":\"carvao\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variants").isEmpty());
    }

    @Test
    void createWarehouse_returns_201() throws Exception {
        Warehouse created = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(estoqueUseCase.createWarehouse("LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA)).thenReturn(created);

        mockMvc.perform(post("/estoque/warehouses")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LOJA-01\",\"name\":\"Loja Centro\",\"type\":\"LOJA_FISICA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("LOJA-01"))
                .andExpect(jsonPath("$.type").value("LOJA_FISICA"));
    }

    @Test
    void createWarehouse_duplicateCode_returns_409() throws Exception {
        when(estoqueUseCase.createWarehouse(eq("LOJA-01"), any(), any()))
                .thenThrow(new DuplicateWarehouseCodeException("LOJA-01"));

        mockMvc.perform(post("/estoque/warehouses")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LOJA-01\",\"name\":\"Loja Centro\",\"type\":\"LOJA_FISICA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_CODE_ALREADY_EXISTS"));
    }

    @Test
    void createWarehouse_withoutCode_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/warehouses")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Loja Centro\",\"type\":\"LOJA_FISICA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWarehouse_withInvalidType_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/warehouses")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LOJA-01\",\"name\":\"Loja Centro\",\"type\":\"INEXISTENTE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listWarehouses_returns_200_paginated() throws Exception {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(estoqueUseCase.listWarehouses(0, 20))
                .thenReturn(new PageResult<>(List.of(warehouse), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("LOJA-01"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listWarehouses_honoursPageAndSize() throws Exception {
        when(estoqueUseCase.listWarehouses(2, 5))
                .thenReturn(new PageResult<>(List.of(), 2, 5, 12L, 3));

        mockMvc.perform(get("/estoque/warehouses").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(estoqueUseCase).listWarehouses(2, 5);
    }

    @Test
    void getStockBalance_returns_200() throws Exception {
        when(estoqueUseCase.getStockBalance("NARG-001", "LOJA-01"))
                .thenReturn(StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("3.000"), 1L));

        mockMvc.perform(get("/estoque/stock-balance").param("sku", "NARG-001").param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("NARG-001"))
                .andExpect(jsonPath("$.warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.quantity").value(3.0));
    }

    @Test
    void getStockBalance_warehouseNotFound_returns_404() throws Exception {
        when(estoqueUseCase.getStockBalance("NARG-001", "INEXISTENTE"))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        mockMvc.perform(get("/estoque/stock-balance").param("sku", "NARG-001").param("warehouseCode", "INEXISTENTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void registerMovement_returns_201_withUpdatedBalance() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("5.000")), eq("Recebimento"), eq("admin")))
                .thenReturn(StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"), 1L));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("NARG-001"))
                .andExpect(jsonPath("$.warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.quantity").value(5.0));
    }

    @Test
    void registerMovement_withInvalidType_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"INEXISTENTE\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerMovement_withoutQuantity_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"reason\":\"Recebimento\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerMovement_withNegativeQuantity_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":-1,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerMovement_warehouseNotFound_returns_404() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("INEXISTENTE"), any(), any(), any(), any()))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"INEXISTENTE\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void registerMovement_skuNotInCatalog_returns_404() throws Exception {
        when(estoqueUseCase.adjustStock(eq("SKU-FANTASMA"), any(), any(), any(), any(), any()))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-FANTASMA\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void setReorderPoint_skuNotInCatalog_returns_404() throws Exception {
        doThrow(new ProductNotFoundException("SKU-FANTASMA"))
                .when(estoqueUseCase).setReorderPoint(eq("SKU-FANTASMA"), any(), any());

        mockMvc.perform(put("/estoque/products/SKU-FANTASMA/reorder-point")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"LOJA-01\",\"minQuantity\":10.000}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void createProduct_dataIntegrityViolation_returns_409_withoutLeakingDriverMessage() throws Exception {
        // EST-C010: rede de segurança — antes, qualquer violação de constraint virava 500 com o
        // texto do driver (nome de tabela, constraint e valores da linha) no corpo da resposta.
        when(estoqueUseCase.createProduct(any(), any(), any(), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "ERROR: duplicate key value violates unique constraint \"uk_product_variant_sku\""));

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile Aladin\",\"category\":\"narguile\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("uk_product_variant_sku"))));
    }

    @Test
    void registerMovement_insufficientStock_returns_400() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.SAIDA), any(), any(), any()))
                .thenThrow(new InsufficientStockException("NARG-001", 1L, new BigDecimal("2.000"), new BigDecimal("5.000")));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"SAIDA\","
                                + "\"quantity\":5.000,\"reason\":\"Venda\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void setReorderPoint_returns_204() throws Exception {
        mockMvc.perform(put("/estoque/products/NARG-001/reorder-point")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"LOJA-01\",\"minQuantity\":10.000}"))
                .andExpect(status().isNoContent());

        verify(estoqueUseCase).setReorderPoint("NARG-001", "LOJA-01", new BigDecimal("10.000"));
    }

    @Test
    void setReorderPoint_withoutWarehouseCode_returns_400() throws Exception {
        mockMvc.perform(put("/estoque/products/NARG-001/reorder-point")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minQuantity\":10.000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setReorderPoint_warehouseNotFound_returns_404() throws Exception {
        doThrow(new WarehouseNotFoundException("INEXISTENTE"))
                .when(estoqueUseCase).setReorderPoint(eq("NARG-001"), eq("INEXISTENTE"), any());

        mockMvc.perform(put("/estoque/products/NARG-001/reorder-point")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"INEXISTENTE\",\"minQuantity\":10.000}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    private StockMovement movement(long id, MovementType type, String quantity, String reason) {
        return StockMovement.of(id, "NARG-001", 1L, type, new BigDecimal(quantity), reason, "gerente",
                Instant.parse("2026-07-26T12:00:00Z"));
    }

    @Test
    void listMovements_returns_200_with_ledger() throws Exception {
        when(estoqueUseCase.listMovements("NARG-001", "LOJA-01", 0, 20))
                .thenReturn(new PageResult<>(List.of(
                        movement(9L, MovementType.SAIDA, "2.000", "Venda balcão sessão #7"),
                        movement(8L, MovementType.ENTRADA, "20.000", "Recebimento de mercadoria - fornecedor #3")),
                        0, 20, 2L, 1));

        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("sku", "NARG-001")
                        .param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(9))
                .andExpect(jsonPath("$.content[0].type").value("SAIDA"))
                .andExpect(jsonPath("$.content[0].sku").value("NARG-001"))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.content[0].username").value("gerente"))
                .andExpect(jsonPath("$.content[0].reason").value("Venda balcão sessão #7"))
                .andExpect(jsonPath("$.content[1].type").value("ENTRADA"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listMovements_returns_200_withEmptyPageWhenNeverMoved() throws Exception {
        when(estoqueUseCase.listMovements("SEM-USO", "LOJA-01", 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("sku", "SEM-USO")
                        .param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /**
     * O teto de 100 deixou de ser {@code Math.min} silencioso e virou {@code @Max(100)} → 400
     * (EST-C005). Como a validação de parâmetro de handler é nativa do
     * {@code RequestMappingHandlerAdapter} e não do controller em si, ela é exercitada com
     * contexto real em {@code EstoqueControllerValidationTest}, não aqui no standalone.
     */
    @Test
    void listMovements_passesPageAndSizeThroughUnchanged() throws Exception {
        when(estoqueUseCase.listMovements("NARG-001", "LOJA-01", 1, 100))
                .thenReturn(new PageResult<>(List.of(), 1, 100, 0L, 0));

        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("sku", "NARG-001")
                        .param("warehouseCode", "LOJA-01")
                        .param("page", "1")
                        .param("size", "100"))
                .andExpect(status().isOk());

        verify(estoqueUseCase).listMovements("NARG-001", "LOJA-01", 1, 100);
    }

    @Test
    void listMovements_withoutSku_returns_400() throws Exception {
        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("warehouseCode", "LOJA-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"));

        verify(estoqueUseCase, never()).listMovements(any(), any(), anyInt(), anyInt());
    }

    @Test
    void listMovements_withoutWarehouseCode_returns_400() throws Exception {
        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("sku", "NARG-001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"));
    }

    @Test
    void listMovements_warehouseNotFound_returns_404() throws Exception {
        when(estoqueUseCase.listMovements("NARG-001", "INEXISTENTE", 0, 20))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("sku", "NARG-001")
                        .param("warehouseCode", "INEXISTENTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void listOrphanSkus_returns_200_with_diagnosticRows() throws Exception {
        OrphanSku orphan = OrphanSku.of("SKU-FANTASMA", "LOJA-01", new BigDecimal("3.000"), 2L, true,
                Instant.parse("2026-07-01T10:00:00Z"));
        when(estoqueUseCase.listOrphanSkus(0, 20))
                .thenReturn(new PageResult<>(List.of(orphan), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/integrity/orphan-skus").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("SKU-FANTASMA"))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.content[0].quantity").value(3.0))
                .andExpect(jsonPath("$.content[0].movementCount").value(2))
                .andExpect(jsonPath("$.content[0].hasReorderPoint").value(true))
                .andExpect(jsonPath("$.content[0].lastMovementAt").exists())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** Órfão que nunca foi movimentado: saldo/ponto de reposição sem ledger. */
    @Test
    void listOrphanSkus_serializesNullLastMovementAt() throws Exception {
        OrphanSku orphan = OrphanSku.of("SKU-FANTASMA", "LOJA-01", new BigDecimal("1.000"), 0L, false, null);
        when(estoqueUseCase.listOrphanSkus(0, 20))
                .thenReturn(new PageResult<>(List.of(orphan), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/integrity/orphan-skus").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lastMovementAt").isEmpty())
                .andExpect(jsonPath("$.content[0].movementCount").value(0));
    }

    /** Base íntegra é 200 com página vazia — não é 404. */
    @Test
    void listOrphanSkus_returns_200_withEmptyPageWhenBaseIsClean() throws Exception {
        when(estoqueUseCase.listOrphanSkus(0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/integrity/orphan-skus").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listOrphanSkus_passesPageAndSizeThroughUnchanged() throws Exception {
        when(estoqueUseCase.listOrphanSkus(3, 100))
                .thenReturn(new PageResult<>(List.of(), 3, 100, 0L, 0));

        mockMvc.perform(get("/estoque/integrity/orphan-skus")
                        .principal(AUTH)
                        .param("page", "3")
                        .param("size", "100"))
                .andExpect(status().isOk());

        verify(estoqueUseCase).listOrphanSkus(3, 100);
    }
}
