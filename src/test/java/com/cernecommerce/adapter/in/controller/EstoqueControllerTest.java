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
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
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
    void listWarehouses_returns_200() throws Exception {
        when(estoqueUseCase.listWarehouses()).thenReturn(
                List.of(Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true)));

        mockMvc.perform(get("/estoque/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("LOJA-01"));
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
}
