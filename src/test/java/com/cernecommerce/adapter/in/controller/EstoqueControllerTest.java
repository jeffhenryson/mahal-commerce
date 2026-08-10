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
import com.cernecommerce.core.domain.exception.storage.ImageTooLargeException;
import com.cernecommerce.core.domain.exception.storage.InvalidImageFormatException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.InactiveProductException;
import com.cernecommerce.core.domain.exception.estoque.InactiveWarehouseException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.LotExpiryDateMismatchException;
import com.cernecommerce.core.domain.exception.estoque.MissingLotInfoException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.UnexpectedLotInfoException;
import com.cernecommerce.core.domain.exception.estoque.StockCountAlreadyOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductType;
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
import com.cernecommerce.core.ports.in.ProductImageUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class EstoqueControllerTest {

    private MockMvc mockMvc;
    private EstoqueUseCase estoqueUseCase;
    private ProductImageUseCase productImageUseCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        estoqueUseCase = mock(EstoqueUseCase.class);
        productImageUseCase = mock(ProductImageUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EstoqueController(estoqueUseCase, new ProductDTOConverter(),
                        new WarehouseDTOConverter(), new StockMovementDTOConverter(),
                        new StockCountDTOConverter(), new StockReservationDTOConverter(),
                        productImageUseCase, publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Product product(String sku) {
        return Product.of(1L, sku, "Narguile Aladin", "narguile", true,
                List.of(ProductVariant.of(1L, sku + "-M", List.of(new ProductAttribute("sabor", "menta")), true)));
    }

    @Test
    void list_returns_200_with_products() throws Exception {
        when(estoqueUseCase.listProducts(eq(0), eq(20), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(product("NARG-001")), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].sku").value("NARG-001"))
                .andExpect(jsonPath("$.content[0].variants[0].attributes[0].type").value("sabor"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_sem_parametros_nao_filtra_e_ordena_por_id_ascendente() throws Exception {
        // Contrato de retrocompatibilidade: quem já chamava só com page/size continua vendo o
        // mesmo comportamento de antes desta entrega.
        when(estoqueUseCase.listProducts(eq(0), eq(20), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/products")).andExpect(status().isOk());

        ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);
        verify(estoqueUseCase).listProducts(eq(0), eq(20), filter.capture(),
                eq(ProductSortField.ID), eq(SortDirection.ASC));
        assertThat(filter.getValue().isEmpty()).isTrue();
    }

    @Test
    void list_repassa_busca_filtros_e_ordenacao_ao_use_case() throws Exception {
        when(estoqueUseCase.listProducts(eq(1), eq(50), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 1, 50, 0L, 0));

        mockMvc.perform(get("/estoque/products")
                        .param("page", "1")
                        .param("size", "50")
                        .param("search", "MeNtA")
                        .param("category", "Narguilé")
                        .param("brand", "Zomo")
                        .param("active", "true")
                        .param("sort", "NAME")
                        .param("direction", "DESC"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);
        verify(estoqueUseCase).listProducts(eq(1), eq(50), filter.capture(),
                eq(ProductSortField.NAME), eq(SortDirection.DESC));
        // Normalizado pelo próprio ProductFilter.
        assertThat(filter.getValue().search()).isEqualTo("menta");
        assertThat(filter.getValue().category()).isEqualTo("narguilé");
        assertThat(filter.getValue().brand()).isEqualTo("zomo");
        assertThat(filter.getValue().active()).isTrue();
    }

    @Test
    void list_com_active_false_filtra_inativos_em_vez_de_ignorar() throws Exception {
        when(estoqueUseCase.listProducts(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/products").param("active", "false"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);
        verify(estoqueUseCase).listProducts(anyInt(), anyInt(), filter.capture(), any(), any());
        assertThat(filter.getValue().active()).isFalse();
    }

    @Test
    void list_com_sort_invalido_returns_400_listando_os_valores_aceitos() throws Exception {
        mockMvc.perform(get("/estoque/products").param("sort", "custo_secreto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ENUM_VALUE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SALE_PRICE")));

        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void getProduct_returns_200_para_sku_existente() throws Exception {
        when(estoqueUseCase.findProductBySku("NARG-001")).thenReturn(product("NARG-001"));

        mockMvc.perform(get("/estoque/products/NARG-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("NARG-001"))
                .andExpect(jsonPath("$.name").value("Narguile Aladin"));
    }

    @Test
    void getProduct_de_sku_inexistente_returns_404() throws Exception {
        when(estoqueUseCase.findProductBySku("SUMIU-001"))
                .thenThrow(new ProductNotFoundException("SUMIU-001"));

        mockMvc.perform(get("/estoque/products/SUMIU-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void getProduct_nao_colide_com_a_rota_literal_de_reorder_points() throws Exception {
        // /estoque/products/reorder-points é rota literal e precisa continuar vencendo o
        // template /estoque/products/{sku} — senão "reorder-points" viraria uma busca por SKU.
        when(estoqueUseCase.listReorderPoints(anyString(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/products/reorder-points").param("warehouseCode", "PRINCIPAL"))
                .andExpect(status().isOk());

        verify(estoqueUseCase, never()).findProductBySku(anyString());
    }

    @Test
    void create_returns_201() throws Exception {
        Product created = product("NARG-001");
        when(estoqueUseCase.createProduct(eq("NARG-001"), eq("Narguile Aladin"), eq("narguile"), any(), any(), any(),
                any(), anyBoolean(), anyBoolean(), any(), any(), any())).thenReturn(created);

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
        when(estoqueUseCase.createProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any()))
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
        when(estoqueUseCase.createProduct(eq("CARV-001"), eq("Carvão Coco"), eq("carvao"), any(), any(), any(),
                any(), anyBoolean(), anyBoolean(), any(), any(), any())).thenReturn(created);

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
    void registerMovement_comLote_passaLoteAdianteERetorna201() throws Exception {
        when(estoqueUseCase.adjustStock(eq("ESS-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("5.000")), eq("Recebimento"), eq("admin"), eq("LOTE-A"),
                eq(LocalDate.of(2027, 3, 1))))
                .thenReturn(StockBalance.of(1L, "ESS-001", 1L, new BigDecimal("5.000"), 1L));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\",\"lotCode\":\"LOTE-A\","
                                + "\"expiryDate\":\"2027-03-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("ESS-001"));
    }

    @Test
    void registerMovement_loteObrigatorioAusente_returns_400() throws Exception {
        when(estoqueUseCase.adjustStock(eq("ESS-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                any(), any(), any()))
                .thenThrow(new MissingLotInfoException("ESS-001"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("LOT_INFO_REQUIRED"));
    }

    @Test
    void registerMovement_loteInesperado_returns_400() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("5.000")), eq("Recebimento"), eq("admin"), eq("LOTE-A"),
                eq(LocalDate.of(2027, 3, 1))))
                .thenThrow(new UnexpectedLotInfoException("NARG-001", "produto não é lote-rastreado"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\",\"lotCode\":\"LOTE-A\","
                                + "\"expiryDate\":\"2027-03-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("LOT_INFO_NOT_APPLICABLE"));
    }

    @Test
    void registerMovement_validadeDivergente_returns_409() throws Exception {
        when(estoqueUseCase.adjustStock(eq("ESS-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("5.000")), eq("Recebimento"), eq("admin"), eq("LOTE-A"),
                eq(LocalDate.of(2027, 3, 1))))
                .thenThrow(new LotExpiryDateMismatchException("ESS-001", "LOTE-A",
                        LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 1)));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\",\"lotCode\":\"LOTE-A\","
                                + "\"expiryDate\":\"2027-03-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LOT_EXPIRY_MISMATCH"));
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
        when(estoqueUseCase.createProduct(any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any()))
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

    // ── Kits (EST-F015) ──────────────────────────────────────────────────────────────────────

    @Test
    void defineKitRecipe_returns_200_withPromotedProduct() throws Exception {
        Product kit = Product.of(1L, "KIT-001", "Kit Narguile", "combo", true, List.of(),
                Pricing.empty(), com.cernecommerce.core.domain.model.estoque.ProductType.KIT);
        when(estoqueUseCase.defineKitRecipe(eq("KIT-001"), any())).thenReturn(kit);

        mockMvc.perform(put("/estoque/products/KIT-001/kit")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"components\":[{\"componentSku\":\"CARV-001\",\"quantity\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("KIT"));
    }

    @Test
    void defineKitRecipe_withoutComponents_returns_400() throws Exception {
        mockMvc.perform(put("/estoque/products/KIT-001/kit")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"components\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defineKitRecipe_kitSkuNotFound_returns_404() throws Exception {
        when(estoqueUseCase.defineKitRecipe(eq("SKU-FANTASMA"), any()))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        mockMvc.perform(put("/estoque/products/SKU-FANTASMA/kit")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"components\":[{\"componentSku\":\"CARV-001\",\"quantity\":2}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void defineKitRecipe_selfReference_returns_409() throws Exception {
        when(estoqueUseCase.defineKitRecipe(eq("KIT-001"), any()))
                .thenThrow(new com.cernecommerce.core.domain.exception.estoque.KitSelfReferenceException("KIT-001"));

        mockMvc.perform(put("/estoque/products/KIT-001/kit")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"components\":[{\"componentSku\":\"KIT-001\",\"quantity\":1}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("KIT_SELF_REFERENCE"));
    }

    @Test
    void defineKitRecipe_componentNotSimples_returns_409() throws Exception {
        when(estoqueUseCase.defineKitRecipe(eq("KIT-001"), any()))
                .thenThrow(new com.cernecommerce.core.domain.exception.estoque.KitComponentNotSimpleException(
                        "KIT-001", "KIT-002", com.cernecommerce.core.domain.model.estoque.ProductType.KIT));

        mockMvc.perform(put("/estoque/products/KIT-001/kit")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"components\":[{\"componentSku\":\"KIT-002\",\"quantity\":1}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("KIT_COMPONENT_NOT_SIMPLE"));
    }

    @Test
    void getKitRecipe_returns_200_withComponentList() throws Exception {
        when(estoqueUseCase.getKitRecipe("KIT-001")).thenReturn(List.of(
                com.cernecommerce.core.domain.model.estoque.KitComponent.of(5L, "KIT-001", "CARV-001",
                        new BigDecimal("2"))));

        mockMvc.perform(get("/estoque/products/KIT-001/kit").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].componentSku").value("CARV-001"))
                .andExpect(jsonPath("$[0].quantity").value(2));
    }

    @Test
    void getKitRecipe_skuNotFound_returns_404() throws Exception {
        when(estoqueUseCase.getKitRecipe("SKU-FANTASMA")).thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        mockMvc.perform(get("/estoque/products/SKU-FANTASMA/kit").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void listStockLots_returns_200_withLotList() throws Exception {
        when(estoqueUseCase.listStockLots("ESS-001", "LOJA-01")).thenReturn(List.of(
                com.cernecommerce.core.domain.model.estoque.StockLot.of(1L, "ESS-001", 1L, "LOTE-A",
                        java.time.LocalDate.of(2027, 1, 1), new BigDecimal("5.000"), null, 0L)));

        mockMvc.perform(get("/estoque/products/ESS-001/lots").param("warehouseCode", "LOJA-01").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("ESS-001"))
                .andExpect(jsonPath("$[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$[0].lotCode").value("LOTE-A"))
                .andExpect(jsonPath("$[0].quantity").value(5.0))
                .andExpect(jsonPath("$[0].alertedAt").isEmpty());
    }

    @Test
    void listStockLots_semLoteRecebido_returns_200_withEmptyList() throws Exception {
        when(estoqueUseCase.listStockLots("NARG-001", "LOJA-01")).thenReturn(List.of());

        mockMvc.perform(get("/estoque/products/NARG-001/lots").param("warehouseCode", "LOJA-01").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listStockLots_warehouseNotFound_returns_404() throws Exception {
        when(estoqueUseCase.listStockLots("ESS-001", "INEXISTENTE"))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        mockMvc.perform(get("/estoque/products/ESS-001/lots").param("warehouseCode", "INEXISTENTE").principal(AUTH))
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

    /** {@code sku} é opcional: omitido, filtra só por depósito — não é mais 400. */
    @Test
    void listMovements_withoutSku_filtersByWarehouseOnly() throws Exception {
        when(estoqueUseCase.listMovements(null, "LOJA-01", 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(estoqueUseCase).listMovements(null, "LOJA-01", 0, 20);
    }

    /**
     * {@code warehouseCode} é opcional: omitido, o feed geral resolve o código de cada depósito
     * distinto na página a partir do {@code warehouseId} da movimentação.
     */
    @Test
    void listMovements_withoutWarehouseCode_resolvesWarehouseCodePerMovement() throws Exception {
        when(estoqueUseCase.listMovements("NARG-001", null, 0, 20))
                .thenReturn(new PageResult<>(List.of(movement(9L, MovementType.SAIDA, "2.000", "Venda balcão")),
                        0, 20, 1L, 1));
        when(estoqueUseCase.getWarehouse(1L))
                .thenReturn(Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true));

        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("sku", "NARG-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"));
    }

    /** Sem nenhum filtro, alimenta o feed geral de movimentações. */
    @Test
    void listMovements_withoutAnyFilter_returns_200() throws Exception {
        when(estoqueUseCase.listMovements(null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/movements").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
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

    @Test
    void listLotMismatches_returns_200_withDiagnosticRows() throws Exception {
        com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch mismatch =
                com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch.of("ESS-001", "LOJA-01",
                        new BigDecimal("10.000"), new BigDecimal("6.000"));
        when(estoqueUseCase.listLotMismatches(0, 20))
                .thenReturn(new PageResult<>(List.of(mismatch), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/integrity/lot-mismatch").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("ESS-001"))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.content[0].balanceQuantity").value(10.0))
                .andExpect(jsonPath("$.content[0].lotsTotal").value(6.0))
                .andExpect(jsonPath("$.content[0].difference").value(4.0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** Base íntegra é 200 com página vazia — não é 404, mesma convenção dos outros diagnósticos. */
    @Test
    void listLotMismatches_returns_200_withEmptyPageWhenIntegrityIsFine() throws Exception {
        when(estoqueUseCase.listLotMismatches(0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/integrity/lot-mismatch").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ------------------------------------------------------------------------------------
    // EST-F018 — PATCH e desativação
    // ------------------------------------------------------------------------------------

    @Test
    void updateProduct_returns_200_withUpdatedBody() throws Exception {
        when(estoqueUseCase.updateProduct("NARG-001", "Narguilé Aladin 2.0", null, null, null, null, null, null,
                null, null, null))
                .thenReturn(Product.of(1L, "NARG-001", "Narguilé Aladin 2.0", "narguile", true, List.of()));

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Narguilé Aladin 2.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Narguilé Aladin 2.0"))
                .andExpect(jsonPath("$.sku").value("NARG-001"));

        verify(estoqueUseCase).updateProduct("NARG-001", "Narguilé Aladin 2.0", null, null, null, null, null, null,
                null, null, null);
    }

    /** Corpo vazio é um no-op válido: nenhum campo veio, nada muda. */
    @Test
    void updateProduct_comCorpoVazio_naoAlteraNada() throws Exception {
        when(estoqueUseCase.updateProduct("NARG-001", null, null, null, null, null, null, null, null, null, null))
                .thenReturn(product("NARG-001"));

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(estoqueUseCase).updateProduct("NARG-001", null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void updateProduct_skuInexistente_returns_404() throws Exception {
        when(estoqueUseCase.updateProduct(eq("SKU-FANTASMA"), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any()))
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

        verify(estoqueUseCase, never()).updateProduct(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any());
    }

    // ------------------------------------------------------------------------------------
    // EST-F019 — precificação
    // ------------------------------------------------------------------------------------

    @Test
    void createProduct_comPricing_repassaAoUseCase() throws Exception {
        when(estoqueUseCase.createProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any()))
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
        verify(estoqueUseCase).createProduct(eq("NARG-001"), any(), any(), any(), captor.capture(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any(), any());
        assertThat(captor.getValue().costPrice()).isEqualByComparingTo("45.00");
    }

    /** Produto sem preço serializa o bloco com os campos nulos — nunca um `pricing` ausente. */
    @Test
    void listProducts_produtoSemPreco_serializaPricingComCamposNulos() throws Exception {
        when(estoqueUseCase.listProducts(eq(0), eq(20), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(product("NARG-001")), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pricing").exists())
                .andExpect(jsonPath("$.content[0].pricing.costPrice").doesNotExist())
                .andExpect(jsonPath("$.content[0].pricing.priced").value(false));
    }

    @Test
    void updateProduct_comPricing_repassaOBlocoAoUseCase() throws Exception {
        when(estoqueUseCase.updateProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any()))
                .thenReturn(Product.of(1L, "NARG-001", "Narguile", "narguile", true, List.of(),
                        Pricing.of(new BigDecimal("60.00"), new BigDecimal("80"), new BigDecimal("79.90"))));

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricing\":{\"costPrice\":60.00}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing.costPrice").value(60.00));

        ArgumentCaptor<Pricing> captor = ArgumentCaptor.forClass(Pricing.class);
        verify(estoqueUseCase).updateProduct(eq("NARG-001"), any(), any(), captor.capture(), any(), any(), any(),
                any(), any(), any(), any());
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

        verify(estoqueUseCase, never()).createProduct(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    // ------------------------------------------------------------------------------------
    // Campos de marketing: originalPrice, superPromo, description, videoUrl, images
    // ------------------------------------------------------------------------------------

    @Test
    void createProduct_comCamposDeMarketing_repassaAoUseCaseERetornaNoBody() throws Exception {
        Product created = Product.create("NARG-001", "Narguile", "narguile", List.of(),
                Pricing.of(new BigDecimal("45.00"), null, new BigDecimal("79.90"), new BigDecimal("99.90")),
                ProductType.SIMPLES, false, "Aladin", "http://img.png", true, true, "Descrição longa",
                "http://video.mp4", List.of("http://img1.png", "http://img2.png"));
        when(estoqueUseCase.createProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any())).thenReturn(created);

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile\",\"category\":\"narguile\","
                                + "\"superPromo\":true,\"description\":\"Descrição longa\","
                                + "\"videoUrl\":\"http://video.mp4\","
                                + "\"images\":[\"http://img1.png\",\"http://img2.png\"],"
                                + "\"pricing\":{\"costPrice\":45.00,\"salePrice\":79.90,\"originalPrice\":99.90}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.superPromo").value(true))
                .andExpect(jsonPath("$.description").value("Descrição longa"))
                .andExpect(jsonPath("$.videoUrl").value("http://video.mp4"))
                .andExpect(jsonPath("$.images[0]").value("http://img1.png"))
                .andExpect(jsonPath("$.images[1]").value("http://img2.png"))
                .andExpect(jsonPath("$.pricing.originalPrice").value(99.90))
                .andExpect(jsonPath("$.pricing.hasDiscount").value(true));

        ArgumentCaptor<Boolean> superPromoCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(estoqueUseCase).createProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), anyBoolean(),
                superPromoCaptor.capture(), eq("Descrição longa"), eq("http://video.mp4"),
                eq(List.of("http://img1.png", "http://img2.png")));
        assertThat(superPromoCaptor.getValue()).isTrue();
    }

    @Test
    void createProduct_descriptionAcimaDoLimite_returns_400() throws Exception {
        String descricaoMuitoLonga = "x".repeat(5001);

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile\","
                                + "\"description\":\"" + descricaoMuitoLonga + "\"}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).createProduct(any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any());
    }

    @Test
    void createProduct_maisDeCincoImagens_returns_400() throws Exception {
        String images = "[\"http://a.png\",\"http://b.png\",\"http://c.png\",\"http://d.png\","
                + "\"http://e.png\",\"http://f.png\"]";

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile\",\"images\":" + images + "}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).createProduct(any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any());
    }

    @Test
    void createProduct_urlDeImagemAcimaDoLimite_returns_400() throws Exception {
        String urlMuitoLonga = "http://img.png/" + "x".repeat(2048);

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile\",\"images\":[\""
                                + urlMuitoLonga + "\"]}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).createProduct(any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any());
    }

    @Test
    void updateProduct_comCamposDeMarketing_repassaAoUseCase() throws Exception {
        Product updated = Product.of(1L, "NARG-001", "Narguile", "narguile", true, List.of(),
                Pricing.empty(), ProductType.SIMPLES, false, null, null, false, true, "Nova descrição",
                "http://video.mp4", List.of("http://img1.png"));
        when(estoqueUseCase.updateProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any())).thenReturn(updated);

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"superPromo\":true,\"description\":\"Nova descrição\","
                                + "\"videoUrl\":\"http://video.mp4\",\"images\":[\"http://img1.png\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superPromo").value(true))
                .andExpect(jsonPath("$.description").value("Nova descrição"))
                .andExpect(jsonPath("$.videoUrl").value("http://video.mp4"))
                .andExpect(jsonPath("$.images[0]").value("http://img1.png"));

        verify(estoqueUseCase).updateProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), eq(true),
                eq("Nova descrição"), eq("http://video.mp4"), eq(List.of("http://img1.png")));
    }

    @Test
    void updateProduct_descriptionAcimaDoLimite_returns_400() throws Exception {
        String descricaoMuitoLonga = "x".repeat(5001);

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + descricaoMuitoLonga + "\"}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).updateProduct(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any());
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
    void setProductLotTracked_returns_200() throws Exception {
        when(estoqueUseCase.setProductLotTracked("ESS-001", true))
                .thenReturn(Product.of(1L, "ESS-001", "Essência", "essencia", true, List.of(),
                        Pricing.empty(), ProductType.SIMPLES, true));

        mockMvc.perform(patch("/estoque/products/ESS-001/lot-tracked")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotTracked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotTracked").value(true));
    }

    @Test
    void setProductLotTracked_semOCampo_returns_400() throws Exception {
        mockMvc.perform(patch("/estoque/products/ESS-001/lot-tracked")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).setProductLotTracked(any(), anyBoolean());
    }

    @Test
    void setProductLotTracked_emKit_returns_400() throws Exception {
        when(estoqueUseCase.setProductLotTracked("KIT-001", true))
                .thenThrow(new IllegalArgumentException(
                        "kit não pode ser lote-rastreado: kit não tem saldo físico próprio (EST-F015)"));

        mockMvc.perform(patch("/estoque/products/KIT-001/lot-tracked")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotTracked\":true}"))
                .andExpect(status().isBadRequest());
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
        when(estoqueUseCase.recordCountedItem(50L, "NARG-001", new BigDecimal("37.000"), null)).thenReturn(updated);
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
        when(estoqueUseCase.recordCountedItem(eq(50L), eq("NARG-001"), any(), isNull())).thenReturn(updated);
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

        verify(estoqueUseCase, never()).recordCountedItem(any(), any(), any(), any());
    }

    @Test
    void recordCountedItem_balancoFechado_returns_409() throws Exception {
        when(estoqueUseCase.recordCountedItem(eq(50L), any(), any(), any()))
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

    // ===== Upload de imagem de produto =====

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x11};

    @Test
    void uploadProductImage_devolve_a_url_no_body_e_repassa_os_bytes_ao_use_case() throws Exception {
        when(productImageUseCase.upload(any())).thenReturn("http://localhost:8082/product-images/abc.jpg");

        mockMvc.perform(multipart("/estoque/products/images")
                        .file(new MockMultipartFile("file", "foto.jpg", "image/jpeg", JPEG_BYTES))
                        .principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("http://localhost:8082/product-images/abc.jpg"));

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(productImageUseCase).upload(captor.capture());
        assertThat(captor.getValue()).isEqualTo(JPEG_BYTES);
    }

    @Test
    void uploadProductImage_arquivo_vazio_returns_400_sem_chamar_o_use_case() throws Exception {
        mockMvc.perform(multipart("/estoque/products/images")
                        .file(new MockMultipartFile("file", "vazio.jpg", "image/jpeg", new byte[0]))
                        .principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_IMAGE_FORMAT"));

        verifyNoInteractions(productImageUseCase);
    }

    @Test
    void uploadProductImage_formato_recusado_pelo_dominio_returns_400() throws Exception {
        when(productImageUseCase.upload(any())).thenThrow(new InvalidImageFormatException());

        mockMvc.perform(multipart("/estoque/products/images")
                        .file(new MockMultipartFile("file", "script.jpg", "image/jpeg", "nao sou imagem".getBytes()))
                        .principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_IMAGE_FORMAT"));
    }

    @Test
    void uploadProductImage_acima_do_limite_returns_400() throws Exception {
        when(productImageUseCase.upload(any())).thenThrow(new ImageTooLargeException(5_242_880L));

        mockMvc.perform(multipart("/estoque/products/images")
                        .file(new MockMultipartFile("file", "grande.jpg", "image/jpeg", JPEG_BYTES))
                        .principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IMAGE_TOO_LARGE"));
    }
}
