package com.cernecommerce.core.service;

import com.cernecommerce.adapter.out.persistence.repository.ProductRepositoryImpl;
import com.cernecommerce.core.domain.exception.compras.UnmatchedNfeLineException;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.NfeImport;
import com.cernecommerce.core.domain.model.compras.NfeImportStatus;
import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.NfeImportUseCase;
import com.cernecommerce.core.ports.in.NfeImportUseCase.LineOverride;
import com.cernecommerce.core.ports.in.NfeImportUseCase.NfeImportConfirmCommand;
import com.cernecommerce.core.ports.out.compras.NfeImportRepository;
import com.cernecommerce.core.ports.out.compras.SupplierRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Importação de NF-e de ponta a ponta contra banco real (EST-F005): preview → confirm →
 * {@code GoodsReceipt}, incluindo o casamento automático por EAN contra o catálogo real e a baixa
 * (alta, neste caso — ENTRADA) de estoque de verdade. Os testes de unidade mockam todas as portas
 * de saída, então nada exercitava o mapeamento `nfe_import`/`nfe_import_line`, nem
 * {@code EstoqueUseCase.findProductByBarcode} contra um produto real.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class NfeImportIT {

    @Autowired NfeImportUseCase nfeImportUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired SupplierRepository supplierRepository;
    @Autowired ProductRepositoryImpl productRepository;
    @Autowired NfeImportRepository nfeImportRepository;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** CNPJ numérico de 14 dígitos, único por teste — não precisa ser válido para o Receita, só distinto. */
    private String uniqueCnpj() {
        return String.valueOf(10_000_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 89_999_999_999_999L));
    }

    private String nfeXml(String cnpj, String ean, String supplierProductCode, String description) {
        return """
                <nfeProc><NFe><infNFe>
                  <emit><CNPJ>%s</CNPJ></emit>
                  <det nItem="1">
                    <prod>
                      <cProd>%s</cProd>
                      %s
                      <xProd>%s</xProd>
                      <qCom>3.000</qCom>
                      <vUnCom>15.00</vUnCom>
                    </prod>
                  </det>
                </infNFe></NFe></nfeProc>
                """.formatted(cnpj, supplierProductCode,
                ean == null ? "<cEAN>SEM GTIN</cEAN>" : "<cEAN>" + ean + "</cEAN>", description);
    }

    @Test
    void fullCycle_previewMatchesByEanAndConfirmCreatesGoodsReceiptWithStockIncrease() {
        String suffix = uniqueSuffix();
        String warehouseCode = "DEP-" + suffix;
        String sku = "PROD-" + suffix;
        String ean = "789" + String.format("%010d", Math.abs(suffix.hashCode()) % 10_000_000_00L);
        String cnpj = uniqueCnpj();

        estoqueUseCase.createWarehouse(warehouseCode, "Depósito " + suffix, WarehouseType.LOJA_FISICA);
        Product product = estoqueUseCase.createProduct(sku, "Produto " + suffix, "Categoria", List.of());
        productRepository.save(product.withBarcode(ean));
        supplierRepository.save(new Supplier(null, "Fornecedor " + suffix, cnpj, "compras@fornecedor.com", true));
        flushAndClear();

        BigDecimal balanceBefore = estoqueUseCase.getStockBalance(sku, warehouseCode).quantity();

        byte[] xml = nfeXml(cnpj, ean, "FORN-" + suffix, "Produto " + suffix).getBytes(StandardCharsets.UTF_8);
        NfeImport preview = nfeImportUseCase.previewImport(xml, "comprador1");

        assertThat(preview.status()).isEqualTo(NfeImportStatus.PREVIEWED);
        assertThat(preview.lines()).singleElement().satisfies(line -> {
            assertThat(line.matchStatus()).isEqualTo(com.cernecommerce.core.domain.model.compras.NfeImportLine.MatchStatus.MATCHED);
            assertThat(line.matchedSku()).isEqualTo(sku);
        });
        flushAndClear();

        GoodsReceipt receipt = nfeImportUseCase.confirmImport(
                new NfeImportConfirmCommand(preview.id(), warehouseCode, List.of()), "comprador1");
        flushAndClear();

        assertThat(receipt.supplierId()).isNotNull();
        assertThat(receipt.warehouseCode()).isEqualTo(warehouseCode);
        assertThat(receipt.items()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo(sku);
            assertThat(item.quantity()).isEqualByComparingTo("3.000");
            assertThat(item.unitCost()).isEqualByComparingTo("15.00");
        });

        BigDecimal balanceAfter = estoqueUseCase.getStockBalance(sku, warehouseCode).quantity();
        assertThat(balanceAfter).isEqualByComparingTo(balanceBefore.add(new BigDecimal("3.000")));

        NfeImport confirmed = requireConfirmed(preview.id());
        assertThat(confirmed.status()).isEqualTo(NfeImportStatus.CONFIRMED);
        assertThat(confirmed.goodsReceiptId()).isEqualTo(receipt.id());
    }

    @Test
    void fullCycle_unmatchedLineBlocksConfirmUntilOverrideIsSupplied() {
        String suffix = uniqueSuffix();
        String warehouseCode = "DEP-" + suffix;
        String sku = "PROD-" + suffix;
        String cnpj = uniqueCnpj();

        estoqueUseCase.createWarehouse(warehouseCode, "Depósito " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Produto " + suffix, "Categoria", List.of());
        supplierRepository.save(new Supplier(null, "Fornecedor " + suffix, cnpj, "compras@fornecedor.com", true));
        flushAndClear();

        // Sem EAN ("SEM GTIN") — nenhum casamento automático possível.
        byte[] xml = nfeXml(cnpj, null, "FORN-" + suffix, "Produto " + suffix).getBytes(StandardCharsets.UTF_8);
        NfeImport preview = nfeImportUseCase.previewImport(xml, "comprador1");
        assertThat(preview.lines()).singleElement().satisfies(line ->
                assertThat(line.matchStatus()).isEqualTo(com.cernecommerce.core.domain.model.compras.NfeImportLine.MatchStatus.UNMATCHED));
        int itemNumber = preview.lines().get(0).itemNumber();
        flushAndClear();

        // Sem override: confirmação bloqueada, nada é recebido.
        assertThatThrownBy(() -> nfeImportUseCase.confirmImport(
                new NfeImportConfirmCommand(preview.id(), warehouseCode, List.of()), "comprador1"))
                .isInstanceOf(UnmatchedNfeLineException.class);

        // Com override: confirma normalmente.
        GoodsReceipt receipt = nfeImportUseCase.confirmImport(new NfeImportConfirmCommand(preview.id(),
                warehouseCode, List.of(new LineOverride(itemNumber, sku))), "comprador1");

        assertThat(receipt.items()).singleElement().satisfies(item -> assertThat(item.sku()).isEqualTo(sku));
    }

    private NfeImport requireConfirmed(Long id) {
        return nfeImportRepository.findById(id).orElseThrow();
    }
}
