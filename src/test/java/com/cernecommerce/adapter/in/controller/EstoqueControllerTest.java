package com.cernecommerce.adapter.in.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.adapter.in.converter.ProductDTOConverter;
import com.cernecommerce.adapter.in.converter.StockCountDTOConverter;
import com.cernecommerce.adapter.in.converter.StockMovementDTOConverter;
import com.cernecommerce.adapter.in.converter.StockReservationDTOConverter;
import com.cernecommerce.adapter.in.converter.WarehouseDTOConverter;
import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.InactiveProductException;
import com.cernecommerce.core.domain.exception.estoque.InactiveWarehouseException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountAlreadyOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReservationIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.ReservationStatus;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
import com.cernecommerce.core.domain.model.estoque.StockCountStatus;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
                        new WarehouseDTOConverter(), new StockMovementDTOConverter(),
                        new StockCountDTOConverter(), new StockReservationDTOConverter(), publisher))
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
        when(estoqueUseCase.createProduct(eq("NARG-001"), eq("Narguile Aladin"), eq("narguile"), any(), any()))
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
        when(estoqueUseCase.createProduct(eq("NARG-001"), any(), any(), any(), any()))
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
        when(estoqueUseCase.createProduct(eq("CARV-001"), eq("Carvão Coco"), eq("carvao"), any(), any()))
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
        when(estoqueUseCase.createProduct(any(), any(), any(), any(), any()))
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

    // ------------------------------------------------------------------------------------
    // EST-F013/EST-F021 — reserva de estoque (listagem) e EST-C013 (integridade)
    // ------------------------------------------------------------------------------------

    @Test
    void listReservations_returns_200_withWarehouseCodeResolved() throws Exception {
        Warehouse loja = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockReservation reservation = StockReservation.of(10L, "NARG-001", 1L, new BigDecimal("2.000"),
                "CHECKOUT:abc", ReservationStatus.ACTIVE, Instant.parse("2026-07-29T12:30:00Z"),
                Instant.parse("2026-07-29T12:00:00Z"), null, "cliente@exemplo.com");
        when(estoqueUseCase.listReservations(null, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(reservation), 0, 20, 1L, 1));
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(loja);

        mockMvc.perform(get("/estoque/reservations").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].sku").value("NARG-001"))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].ownerReference").value("CHECKOUT:abc"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listReservations_passesFiltersThrough() throws Exception {
        when(estoqueUseCase.listReservations("NARG-001", "LOJA-01", ReservationStatus.ACTIVE, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/reservations")
                        .principal(AUTH)
                        .param("sku", "NARG-001")
                        .param("warehouseCode", "LOJA-01")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk());

        verify(estoqueUseCase).listReservations("NARG-001", "LOJA-01", ReservationStatus.ACTIVE, 0, 20);
    }

    @Test
    void getReservation_returns_200() throws Exception {
        Warehouse loja = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockReservation reservation = StockReservation.of(10L, "NARG-001", 1L, new BigDecimal("2.000"),
                "CHECKOUT:abc", ReservationStatus.ACTIVE, Instant.parse("2026-07-29T12:30:00Z"),
                Instant.parse("2026-07-29T12:00:00Z"), null, "cliente@exemplo.com");
        when(estoqueUseCase.getStockReservation(10L)).thenReturn(reservation);
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(loja);

        mockMvc.perform(get("/estoque/reservations/10").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.warehouseCode").value("LOJA-01"));
    }

    @Test
    void getReservation_returns_404_whenNotFound() throws Exception {
        when(estoqueUseCase.getStockReservation(999L))
                .thenThrow(new StockReservationNotFoundException(999L));

        mockMvc.perform(get("/estoque/reservations/999").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void listReservationMismatches_returns_200_withDiagnosticRows() throws Exception {
        ReservationIntegrityMismatch mismatch = ReservationIntegrityMismatch.of("NARG-001", "LOJA-01",
                new BigDecimal("5.000"), new BigDecimal("3.000"));
        when(estoqueUseCase.listReservationMismatches(0, 20))
                .thenReturn(new PageResult<>(List.of(mismatch), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/integrity/reservation-mismatch").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("NARG-001"))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.content[0].reservedQuantity").value(5.0))
                .andExpect(jsonPath("$.content[0].activeReservationsTotal").value(3.0))
                .andExpect(jsonPath("$.content[0].difference").value(2.0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** Base íntegra é 200 com página vazia — não é 404, mesma convenção de listOrphanSkus. */
    @Test
    void listReservationMismatches_returns_200_withEmptyPageWhenIntegrityIsFine() throws Exception {
        when(estoqueUseCase.listReservationMismatches(0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/integrity/reservation-mismatch").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ------------------------------------------------------------------------------------
    // EST-F018 — PATCH e desativação
    // ------------------------------------------------------------------------------------

    @Test
    void updateProduct_returns_200_withUpdatedBody() throws Exception {
        when(estoqueUseCase.updateProduct("NARG-001", "Narguilé Aladin 2.0", null, null))
                .thenReturn(Product.of(1L, "NARG-001", "Narguilé Aladin 2.0", "narguile", true, List.of()));

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Narguilé Aladin 2.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Narguilé Aladin 2.0"))
                .andExpect(jsonPath("$.sku").value("NARG-001"));

        verify(estoqueUseCase).updateProduct("NARG-001", "Narguilé Aladin 2.0", null, null);
    }

    /** Corpo vazio é um no-op válido: nenhum campo veio, nada muda. */
    @Test
    void updateProduct_comCorpoVazio_naoAlteraNada() throws Exception {
        when(estoqueUseCase.updateProduct("NARG-001", null, null, null))
                .thenReturn(product("NARG-001"));

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(estoqueUseCase).updateProduct("NARG-001", null, null, null);
    }

    @Test
    void updateProduct_skuInexistente_returns_404() throws Exception {
        when(estoqueUseCase.updateProduct(eq("SKU-FANTASMA"), any(), any(), any()))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        mockMvc.perform(patch("/estoque/products/SKU-FANTASMA")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novo\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void updateProduct_comNomeVazio_returns_400() throws Exception {
        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).updateProduct(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------------------------
    // EST-F019 — precificação
    // ------------------------------------------------------------------------------------

    @Test
    void createProduct_comPricing_repassaAoUseCase() throws Exception {
        when(estoqueUseCase.createProduct(eq("NARG-001"), any(), any(), any(), any()))
                .thenReturn(Product.of(1L, "NARG-001", "Narguile", "narguile", true, List.of(),
                        Pricing.of(new BigDecimal("45.00"), new BigDecimal("80"), new BigDecimal("79.90"))));

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile\","
                                + "\"pricing\":{\"costPrice\":45.00,\"markupPercent\":80,\"salePrice\":79.90}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pricing.costPrice").value(45.00))
                .andExpect(jsonPath("$.pricing.effectivePrice").value(79.90));

        ArgumentCaptor<Pricing> captor = ArgumentCaptor.forClass(Pricing.class);
        verify(estoqueUseCase).createProduct(eq("NARG-001"), any(), any(), any(), captor.capture());
        assertThat(captor.getValue().costPrice()).isEqualByComparingTo("45.00");
    }

    /** Produto sem preço serializa o bloco com os campos nulos — nunca um `pricing` ausente. */
    @Test
    void listProducts_produtoSemPreco_serializaPricingComCamposNulos() throws Exception {
        when(estoqueUseCase.listProducts(0, 20))
                .thenReturn(new PageResult<>(List.of(product("NARG-001")), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pricing").exists())
                .andExpect(jsonPath("$.content[0].pricing.costPrice").doesNotExist())
                .andExpect(jsonPath("$.content[0].pricing.priced").value(false));
    }

    @Test
    void updateProduct_comPricing_repassaOBlocoAoUseCase() throws Exception {
        when(estoqueUseCase.updateProduct(eq("NARG-001"), any(), any(), any()))
                .thenReturn(Product.of(1L, "NARG-001", "Narguile", "narguile", true, List.of(),
                        Pricing.of(new BigDecimal("60.00"), new BigDecimal("80"), new BigDecimal("79.90"))));

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricing\":{\"costPrice\":60.00}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing.costPrice").value(60.00));

        ArgumentCaptor<Pricing> captor = ArgumentCaptor.forClass(Pricing.class);
        verify(estoqueUseCase).updateProduct(eq("NARG-001"), any(), any(), captor.capture());
        assertThat(captor.getValue().costPrice()).isEqualByComparingTo("60.00");
        assertThat(captor.getValue().markupPercent()).as("campo ausente vira nulo = manter").isNull();
    }

    @Test
    void createProduct_comCustoNegativo_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile\","
                                + "\"pricing\":{\"costPrice\":-1.00}}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).createProduct(any(), any(), any(), any(), any());
    }

    @Test
    void getProductPrice_returns_200_comDerivados() throws Exception {
        when(estoqueUseCase.findPricingBySku("NARG-001"))
                .thenReturn(Pricing.of(new BigDecimal("45.00"), new BigDecimal("80"), new BigDecimal("79.90")));

        mockMvc.perform(get("/estoque/products/NARG-001/price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedPrice").value(81.00))
                .andExpect(jsonPath("$.effectivePrice").value(79.90))
                .andExpect(jsonPath("$.marginAmount").value(34.90))
                .andExpect(jsonPath("$.marginPercent").value(43.68))
                .andExpect(jsonPath("$.effectiveMarkupPercent").value(77.56))
                .andExpect(jsonPath("$.priced").value(true))
                .andExpect(jsonPath("$.belowCost").value(false));
    }

    @Test
    void getProductPrice_produtoSemPreco_returns_200_comCamposNulos() throws Exception {
        when(estoqueUseCase.findPricingBySku("NARG-001")).thenReturn(Pricing.empty());

        mockMvc.perform(get("/estoque/products/NARG-001/price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectivePrice").doesNotExist())
                .andExpect(jsonPath("$.priced").value(false));
    }

    @Test
    void getProductPrice_skuInexistente_returns_404() throws Exception {
        when(estoqueUseCase.findPricingBySku("SKU-FANTASMA"))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        mockMvc.perform(get("/estoque/products/SKU-FANTASMA/price"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void setProductActive_returns_200() throws Exception {
        when(estoqueUseCase.setProductActive("NARG-001", false))
                .thenReturn(Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", false, List.of()));

        mockMvc.perform(patch("/estoque/products/NARG-001/active")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    /** {@code Boolean} + {@code @NotNull} para o corpo vazio não virar um "desativar" silencioso. */
    @Test
    void setProductActive_semOCampoActive_returns_400() throws Exception {
        mockMvc.perform(patch("/estoque/products/NARG-001/active")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).setProductActive(any(), anyBoolean());
    }

    @Test
    void updateWarehouse_returns_200() throws Exception {
        when(estoqueUseCase.updateWarehouse("LOJA-01", "Loja Reformada", null))
                .thenReturn(Warehouse.of(1L, "LOJA-01", "Loja Reformada", WarehouseType.LOJA_FISICA, true));

        mockMvc.perform(patch("/estoque/warehouses/LOJA-01")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Loja Reformada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Loja Reformada"))
                .andExpect(jsonPath("$.type").value("LOJA_FISICA"));
    }

    @Test
    void updateWarehouse_comTipoDesconhecido_returns_400() throws Exception {
        mockMvc.perform(patch("/estoque/warehouses/LOJA-01")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GALPAO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateWarehouse_inexistente_returns_404() throws Exception {
        when(estoqueUseCase.updateWarehouse(eq("INEXISTENTE"), any(), any()))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        mockMvc.perform(patch("/estoque/warehouses/INEXISTENTE")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novo\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void setWarehouseActive_returns_200() throws Exception {
        when(estoqueUseCase.setWarehouseActive("LOJA-01", false))
                .thenReturn(Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, false));

        mockMvc.perform(patch("/estoque/warehouses/LOJA-01/active")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void registerMovement_entradaEmProdutoDesativado_returns_409() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                any(), any(), any()))
                .thenThrow(new InactiveProductException("NARG-001"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_INACTIVE"));
    }

    @Test
    void registerMovement_entradaEmDepositoDesativado_returns_409() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                any(), any(), any()))
                .thenThrow(new InactiveWarehouseException("LOJA-01"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_INACTIVE"));
    }

    // ------------------------------------------------------------------------------------
    // EST-F006 — balanço de inventário
    // ------------------------------------------------------------------------------------

    private static final Warehouse LOJA =
            Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);

    private StockCount count(StockCountStatus status, List<StockCountItem> items) {
        return StockCount.of(50L, 1L, status, "gerente",
                Instant.parse("2026-07-27T09:00:00Z"),
                status == StockCountStatus.ABERTA ? null : Instant.parse("2026-07-27T18:00:00Z"), items);
    }

    @Test
    void openStockCount_returns_201_withLocation() throws Exception {
        when(estoqueUseCase.openStockCount("LOJA-01", "admin"))
                .thenReturn(count(StockCountStatus.ABERTA, List.of()));

        mockMvc.perform(post("/estoque/stock-counts")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"LOJA-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/estoque/stock-counts/50"))
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void openStockCount_segundoNoMesmoDeposito_returns_409() throws Exception {
        when(estoqueUseCase.openStockCount(eq("LOJA-01"), any()))
                .thenThrow(new StockCountAlreadyOpenException("LOJA-01", 50L));

        mockMvc.perform(post("/estoque/stock-counts")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"LOJA-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_COUNT_ALREADY_OPEN"));
    }

    @Test
    void openStockCount_depositoInexistente_returns_404() throws Exception {
        when(estoqueUseCase.openStockCount(eq("INEXISTENTE"), any()))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        mockMvc.perform(post("/estoque/stock-counts")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"INEXISTENTE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void recordCountedItem_returns_200() throws Exception {
        StockCount updated = count(StockCountStatus.ABERTA,
                List.of(StockCountItem.of(1L, "NARG-001", new BigDecimal("37.000"), null, null)));
        when(estoqueUseCase.recordCountedItem(50L, "NARG-001", new BigDecimal("37.000"))).thenReturn(updated);
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(LOJA);

        mockMvc.perform(post("/estoque/stock-counts/50/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"countedQuantity\":37.000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sku").value("NARG-001"))
                .andExpect(jsonPath("$.items[0].countedQuantity").value(37.0))
                .andExpect(jsonPath("$.items[0].expectedQuantity").isEmpty())
                .andExpect(jsonPath("$.warehouseCode").value("LOJA-01"));
    }

    /** Contar zero é o item que sumiu — o teto inferior é inclusivo de propósito. */
    @Test
    void recordCountedItem_contagemZero_returns_200() throws Exception {
        StockCount updated = count(StockCountStatus.ABERTA,
                List.of(StockCountItem.of(1L, "NARG-001", BigDecimal.ZERO, null, null)));
        when(estoqueUseCase.recordCountedItem(eq(50L), eq("NARG-001"), any())).thenReturn(updated);
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(LOJA);

        mockMvc.perform(post("/estoque/stock-counts/50/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"countedQuantity\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void recordCountedItem_contagemNegativa_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts/50/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"countedQuantity\":-1}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).recordCountedItem(any(), any(), any());
    }

    @Test
    void recordCountedItem_balancoFechado_returns_409() throws Exception {
        when(estoqueUseCase.recordCountedItem(eq(50L), any(), any()))
                .thenThrow(new StockCountNotOpenException(50L, StockCountStatus.FECHADA));

        mockMvc.perform(post("/estoque/stock-counts/50/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"countedQuantity\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_COUNT_NOT_OPEN"));
    }

    @Test
    void closeStockCount_returns_200_withDivergences() throws Exception {
        StockCount closed = count(StockCountStatus.FECHADA, List.of(
                StockCountItem.of(1L, "SKU-FALTA", new BigDecimal("8.000"),
                        new BigDecimal("10.000"), new BigDecimal("-2.000"))));
        when(estoqueUseCase.closeStockCount(50L, "admin")).thenReturn(closed);
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(LOJA);

        mockMvc.perform(post("/estoque/stock-counts/50/close").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].expectedQuantity").value(10.0))
                .andExpect(jsonPath("$.items[0].difference").value(-2.0));
    }

    @Test
    void closeStockCount_balancoJaFechado_returns_409() throws Exception {
        when(estoqueUseCase.closeStockCount(eq(50L), any()))
                .thenThrow(new StockCountNotOpenException(50L, StockCountStatus.FECHADA));

        mockMvc.perform(post("/estoque/stock-counts/50/close").principal(AUTH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_COUNT_NOT_OPEN"));
    }

    @Test
    void closeStockCount_inexistente_returns_404() throws Exception {
        when(estoqueUseCase.closeStockCount(eq(99L), any()))
                .thenThrow(new StockCountNotFoundException(99L));

        mockMvc.perform(post("/estoque/stock-counts/99/close").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STOCK_COUNT_NOT_FOUND"));
    }

    @Test
    void cancelStockCount_returns_200() throws Exception {
        when(estoqueUseCase.cancelStockCount(50L))
                .thenReturn(count(StockCountStatus.CANCELADA, List.of()));
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(LOJA);

        mockMvc.perform(post("/estoque/stock-counts/50/cancel").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    void getStockCount_returns_200() throws Exception {
        when(estoqueUseCase.getStockCount(50L)).thenReturn(count(StockCountStatus.ABERTA, List.of()));
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(LOJA);

        mockMvc.perform(get("/estoque/stock-counts/50").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.username").value("gerente"));
    }

    @Test
    void getStockCount_inexistente_returns_404() throws Exception {
        when(estoqueUseCase.getStockCount(99L)).thenThrow(new StockCountNotFoundException(99L));

        mockMvc.perform(get("/estoque/stock-counts/99").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STOCK_COUNT_NOT_FOUND"));
    }

    @Test
    void listStockCounts_returns_200_paginated() throws Exception {
        when(estoqueUseCase.listStockCounts("LOJA-01", 0, 20))
                .thenReturn(new PageResult<>(List.of(count(StockCountStatus.ABERTA, List.of())), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/stock-counts").principal(AUTH).param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(50))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listStockCounts_semWarehouseCode_returns_400() throws Exception {
        mockMvc.perform(get("/estoque/stock-counts").principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"));
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
