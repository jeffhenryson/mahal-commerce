package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.compras.DuplicateSupplierTaxIdException;
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
                eq(new BigDecimal("10.000")), anyString(), eq("gerente"), isNull(), isNull(), isNull(), isNull());
        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("5.000")), anyString(), eq("gerente"), isNull(), isNull(), isNull(), isNull());
        verify(goodsReceiptRepository).save(any());
    }

    @Test
    void receiveGoods_propagaIdDoRecebimentoSalvoParaAdjustStock() {
        // item 2 do pedido do frontend: o vínculo stock_movement.goods_receipt_id só faz sentido
        // se o id atribuído pelo banco ao salvar o GoodsReceipt for o mesmo usado em cada
        // adjustStock — não um id fantasma calculado antes da gravação.
        List<GoodsReceiptItem> items = List.of(new GoodsReceiptItem("NARG-001", new BigDecimal("10.000")));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier()));
        when(goodsReceiptRepository.save(any())).thenAnswer(inv -> {
            GoodsReceipt receipt = inv.getArgument(0);
            return GoodsReceipt.of(42L, receipt.supplierId(), receipt.warehouseCode(), receipt.items(),
                    receipt.username(), receipt.receivedAt());
        });

        GoodsReceipt result = comprasService.receiveGoods(1L, "LOJA-01", items, "gerente");

        assertThat(result.id()).isEqualTo(42L);
        verify(estoqueUseCase).adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("10.000")), anyString(), eq("gerente"), isNull(), isNull(), isNull(), eq(42L));
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
                eq(LocalDate.parse("2027-01-01")), isNull(), isNull());
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
                eq(new BigDecimal("7.50")), isNull());
    }

    @Test
    void receiveGoods_semUnitCost_fluxoLegadoContinuaFuncionando() {
        List<GoodsReceiptItem> items = List.of(new GoodsReceiptItem("NARG-001", new BigDecimal("10.000")));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier()));
        when(goodsReceiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GoodsReceipt result = comprasService.receiveGoods(1L, "LOJA-01", items, "gerente");

        assertThat(result.items().get(0).unitCost()).isNull();
        verify(estoqueUseCase).adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("10.000")), anyString(), eq("gerente"), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void receiveGoods_throwsWhenSupplierNotFound() {
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comprasService.receiveGoods(99L, "LOJA-01",
                List.of(new GoodsReceiptItem("NARG-001", BigDecimal.ONE)), "gerente"))
                .isInstanceOf(SupplierNotFoundException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any());
        verify(goodsReceiptRepository, never()).save(any());
    }

    @Test
    void receiveGoods_propagatesUnknownSkuException_afterSavingReceiptForId() {
        // EST-C002: recebimento com SKU não cadastrado continua revertendo a entrada de saldo.
        // O GoodsReceipt é salvo ANTES do loop de itens (item 2: precisa do id para linkar cada
        // ENTRADA de volta a ele) — a exceção no meio do loop propaga normalmente, e é a mesma
        // transação (@Transactional) quem desfaz o save do recebimento junto, não este teste
        // unitário, que só confirma que a exceção não é engolida.
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier()));
        when(goodsReceiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        assertThatThrownBy(() -> comprasService.receiveGoods(1L, "LOJA-01",
                List.of(new GoodsReceiptItem("SKU-FANTASMA", BigDecimal.ONE)), "gerente"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void receiveGoods_propagatesExceptionFromEstoque() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier()));
        when(goodsReceiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        assertThatThrownBy(() -> comprasService.receiveGoods(1L, "INEXISTENTE",
                List.of(new GoodsReceiptItem("NARG-001", BigDecimal.ONE)), "gerente"))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    // ── Cadastro de fornecedor (COM-F001) ────────────────────────────────────────────────────

    /**
     * O CNPJ é gravado <b>só com dígitos</b>, que é como o XML da NF-e traz o emitente. Sem isso,
     * o fornecedor cadastrado pela tela com máscara nunca casaria com a nota importada — e a
     * importação continuaria respondendo 404, que é justamente o que este card veio resolver.
     */
    @Test
    void registerSupplier_normalizaOTaxIdParaSoDigitos() {
        when(supplierRepository.findByTaxId("12345678000190")).thenReturn(Optional.empty());
        when(supplierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Supplier criado = comprasService.registerSupplier("Distribuidora Zomo LTDA",
                "12.345.678/0001-90", "contato@zomo.com.br");

        assertThat(criado.taxId()).isEqualTo("12345678000190");
        assertThat(criado.active()).isTrue();
    }

    /**
     * A duplicidade é conferida sobre o valor <b>normalizado</b>: com e sem máscara são o mesmo
     * fornecedor. Comparar o texto cru deixaria a uk_supplier_tax_id passar batido e criaria dois
     * cadastros para o mesmo CNPJ.
     */
    @Test
    void registerSupplier_recusaOMesmoCnpjComOutraMascara() {
        when(supplierRepository.findByTaxId("12345678000190")).thenReturn(Optional.of(supplier()));

        assertThatThrownBy(() -> comprasService.registerSupplier("Outro Nome",
                "12.345.678/0001-90", null))
                .isInstanceOf(DuplicateSupplierTaxIdException.class);

        verify(supplierRepository, never()).save(any());
    }

    /** CPF é aceito: produtor rural que emite nota é pessoa física. */
    @Test
    void registerSupplier_aceitaCpf() {
        when(supplierRepository.findByTaxId("12345678901")).thenReturn(Optional.empty());
        when(supplierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(comprasService.registerSupplier("Sítio do Zé", "123.456.789-01", null).taxId())
                .isEqualTo("12345678901");
    }

    @Test
    void registerSupplier_recusaTaxIdComTamanhoInvalido() {
        assertThatThrownBy(() -> comprasService.registerSupplier("Fornecedor", "123", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taxId");

        verify(supplierRepository, never()).save(any());
    }

    /** PATCH parcial: campo nulo mantém, e o taxId nunca muda — é a chave da importação de NF-e. */
    @Test
    void updateSupplier_mantemOQueNaoVeio_eNuncaTrocaOTaxId() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier()));
        when(supplierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Supplier atualizado = comprasService.updateSupplier(1L, "Fornecedor Teste ME", null);

        assertThat(atualizado.legalName()).isEqualTo("Fornecedor Teste ME");
        assertThat(atualizado.email()).isEqualTo("contato@fornecedor.com");
        assertThat(atualizado.taxId()).isEqualTo("12345678000190");
    }

    @Test
    void setSupplierActive_desativaSemApagar() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier()));
        when(supplierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(comprasService.setSupplierActive(1L, false).active()).isFalse();
    }

    @Test
    void updateSupplier_comIdInexistente_lanca404() {
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comprasService.updateSupplier(99L, "Nome", null))
                .isInstanceOf(SupplierNotFoundException.class);
    }
}
