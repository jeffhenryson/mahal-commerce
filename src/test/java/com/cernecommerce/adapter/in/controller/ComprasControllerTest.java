package com.cernecommerce.adapter.in.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.adapter.in.converter.GoodsReceiptDTOConverter;
import com.cernecommerce.core.domain.exception.compras.SupplierNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.GoodsReceiptItem;
import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.ports.in.ComprasUseCase;
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

public class ComprasControllerTest {

    private MockMvc mockMvc;
    private ComprasUseCase comprasUseCase;
    private ApplicationEventPublisher publisher;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        comprasUseCase = mock(ComprasUseCase.class);
        publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ComprasController(comprasUseCase, new GoodsReceiptDTOConverter(), publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listSuppliers_returns_200() throws Exception {
        Supplier supplier = new Supplier(1L, "Fornecedor A", "12345678901234", "contato@fornecedora.com", true);
        when(comprasUseCase.listSuppliers(0, 20))
                .thenReturn(new PageResult<>(List.of(supplier), 0, 20, 1L, 1));

        mockMvc.perform(get("/compras/suppliers").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].legalName").value("Fornecedor A"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void receiveGoods_returns_201() throws Exception {
        GoodsReceiptItem item = new GoodsReceiptItem("SKU-001", new BigDecimal("10.0"));
        GoodsReceipt receipt = GoodsReceipt.of(100L, 1L, "LOJA-01", List.of(item), "admin", Instant.now());
        
        when(comprasUseCase.receiveGoods(eq(1L), eq("LOJA-01"), any(), eq("admin")))
                .thenReturn(receipt);

        String json = """
                {
                    "supplierId": 1,
                    "warehouseCode": "LOJA-01",
                    "items": [
                        {
                            "sku": "SKU-001",
                            "quantity": 10.0
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/compras/goods-receipts")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.supplierId").value(1))
                .andExpect(jsonPath("$.warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.items[0].quantity").value(10.0));
                
        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void receiveGoods_withoutSupplier_returns_400() throws Exception {
        String json = """
                {
                    "warehouseCode": "LOJA-01",
                    "items": [
                        {
                            "sku": "SKU-001",
                            "quantity": 10.0
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/compras/goods-receipts")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void receiveGoods_warehouseNotFound_returns_404() throws Exception {
        when(comprasUseCase.receiveGoods(eq(1L), eq("INEXISTENTE"), any(), eq("admin")))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        String json = """
                {
                    "supplierId": 1,
                    "warehouseCode": "INEXISTENTE",
                    "items": [
                        {
                            "sku": "SKU-001",
                            "quantity": 10.0
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/compras/goods-receipts")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }
    
    @Test
    void receiveGoods_supplierNotFound_returns_404() throws Exception {
        when(comprasUseCase.receiveGoods(eq(99L), eq("LOJA-01"), any(), eq("admin")))
                .thenThrow(new SupplierNotFoundException(99L));

        String json = """
                {
                    "supplierId": 99,
                    "warehouseCode": "LOJA-01",
                    "items": [
                        {
                            "sku": "SKU-001",
                            "quantity": 10.0
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/compras/goods-receipts")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SUPPLIER_NOT_FOUND"));
    }
}
