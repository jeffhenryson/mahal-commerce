package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.compras.SupplierNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.GoodsReceiptItem;
import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.compras.GoodsReceiptRepository;
import com.cernecommerce.core.ports.out.compras.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComprasServiceTest {

    @Mock SupplierRepository supplierRepository;
    @Mock GoodsReceiptRepository goodsReceiptRepository;
    @Mock EstoqueUseCase estoqueUseCase;

    ComprasService comprasService;

    @BeforeEach
    void setUp() {
        comprasService = new ComprasService(supplierRepository, goodsReceiptRepository, estoqueUseCase);
    }

    private Supplier supplier() {
        return new Supplier(1L, "Fornecedor Teste LTDA", "12345678000190", "contato@fornecedor.com", true);
    }

    @Test
    void listSuppliers_delegatesToRepository() {
        PageResult<Supplier> page = new PageResult<>(List.of(supplier()), 0, 20, 1L, 1);
        when(supplierRepository.findAll(0, 20)).thenReturn(page);

        PageResult<Supplier> result = comprasService.listSuppliers(0, 20);

        assertThat(result.content()).hasSize(1);
        verify(supplierRepository).findAll(0, 20);
    }

    @Test
    void receiveGoods_adjustsStockPerItemAndSavesReceipt() {
        List<GoodsReceiptItem> items = List.of(
                new GoodsReceiptItem("NARG-001", new BigDecimal("10.000")),
                new GoodsReceiptItem("CARV-001", new BigDecimal("5.000")));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier()));
        when(goodsReceiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GoodsReceipt result = comprasService.receiveGoods(1L, "LOJA-01", items, "gerente");

        assertThat(result.supplierId()).isEqualTo(1L);
        assertThat(result.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(result.items()).hasSize(2);
        verify(estoqueUseCase).adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("10.000")), anyString(), eq("gerente"), isNull(), isNull(), isNull());
        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("5.000")), anyString(), eq("gerente"), isNull(), isNull(), isNull());
        verify(goodsReceiptRepository).save(any());
    }

    @Test
    void receiveGoods_loteRastreado_propagaLoteParaAdjustStock() {
        List<GoodsReceiptItem> items = List.of(
                new GoodsReceiptItem("ESSE-001", new BigDecimal("10.000"), "L1", LocalDate.parse("2027-01-01")));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier()));
        when(goodsReceiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comprasService.receiveGoods(1L, "LOJA-01", items, "gerente");

        verify(estoqueUseCase).adjustStock(eq("ESSE-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("10.000")), anyString(), eq("gerente"), eq("L1"),
                eq(LocalDate.parse("2027-01-01")), isNull());
    }

    @Test
    void receiveGoods_comUnitCost_propagaCustoParaAdjustStock() {
        List<GoodsReceiptItem> items = List.of(
                new GoodsReceiptItem("NARG-001", new BigDecimal("10.000"), null, null, new BigDecimal("7.50")));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier()));
        when(goodsReceiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comprasService.receiveGoods(1L, "LOJA-01", items, "gerente");

        verify(estoqueUseCase).adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("10.000")), anyString(), eq("gerente"), isNull(), isNull(),
                eq(new BigDecimal("7.50")));
    }

    @Test
    void receiveGoods_semUnitCost_fluxoLegadoContinuaFuncionando() {
        List<GoodsReceiptItem> items = List.of(new GoodsReceiptItem("NARG-001", new BigDecimal("10.000")));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier()));
        when(goodsReceiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GoodsReceipt result = comprasService.receiveGoods(1L, "LOJA-01", items, "gerente");

        assertThat(result.items().get(0).unitCost()).isNull();
        verify(estoqueUseCase).adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("10.000")), anyString(), eq("gerente"), isNull(), isNull(), isNull());
    }

    @Test
    void receiveGoods_throwsWhenSupplierNotFound() {
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comprasService.receiveGoods(99L, "LOJA-01",
                List.of(new GoodsReceiptItem("NARG-001", BigDecimal.ONE)), "gerente"))
                .isInstanceOf(SupplierNotFoundException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(goodsReceiptRepository, never()).save(any());
    }

    @Test
    void receiveGoods_propagatesUnknownSkuAndDoesNotSaveReceipt() {
        // EST-C002: recebimento com SKU não cadastrado agora reverte tudo, em vez de dar entrada
        // de saldo num SKU que não existe no catálogo.
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier()));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        assertThatThrownBy(() -> comprasService.receiveGoods(1L, "LOJA-01",
                List.of(new GoodsReceiptItem("SKU-FANTASMA", BigDecimal.ONE)), "gerente"))
                .isInstanceOf(ProductNotFoundException.class);

        verify(goodsReceiptRepository, never()).save(any());
    }

    @Test
    void receiveGoods_propagatesExceptionFromEstoqueAndDoesNotSaveReceipt() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier()));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        assertThatThrownBy(() -> comprasService.receiveGoods(1L, "INEXISTENTE",
                List.of(new GoodsReceiptItem("NARG-001", BigDecimal.ONE)), "gerente"))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(goodsReceiptRepository, never()).save(any());
    }
}
