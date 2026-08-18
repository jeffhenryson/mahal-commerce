package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.NfeImport;

import java.util.List;

/**
 * Port de entrada da importação de entrada de mercadoria por XML de NF-e (EST-F005).
 *
 * <p>Fluxo em duas fases — {@link #previewImport} → {@link #confirmImport} — porque
 * {@code ComprasUseCase.receiveGoods} é transacional tudo-ou-nada, e uma NF-e real com item sem
 * EAN batido (comum: {@code cEAN} ausente ou "SEM GTIN") não pode abortar o recebimento inteiro.
 * O operador resolve a pendência manualmente no fechamento, sem precisar editar o XML do
 * fornecedor.</p>
 */
public interface NfeImportUseCase {

    /**
     * Parseia o XML, casa o fornecedor pelo CNPJ do emitente e casa cada item pelo EAN — quando
     * bate com um {@code Product.barcode} do catálogo. Persiste o XML bruto e o resultado do
     * parsing (inclusive quando o fornecedor não é encontrado, como {@code REJECTED} — trilha de
     * auditoria de toda tentativa de import).
     *
     * @throws com.cernecommerce.core.domain.exception.compras.MalformedNfeXmlException se o XML
     *         não for bem formado ou for rejeitado pelo hardening contra XXE
     * @throws com.cernecommerce.core.domain.exception.compras.SupplierNotFoundByTaxIdException se
     *         nenhum fornecedor cadastrado bate com o CNPJ do emitente
     */
    NfeImport previewImport(byte[] xmlBytes, String username);

    /**
     * Confirma um import previamente aceito: exige SKU resolvido (por EAN ou por override) em
     * toda linha, então delega para {@code ComprasUseCase.receiveGoods}.
     *
     * @throws com.cernecommerce.core.domain.exception.compras.NfeImportNotFoundException se o
     *         import não existir
     * @throws com.cernecommerce.core.domain.exception.compras.NfeImportAlreadyProcessedException
     *         se o import já tiver sido confirmado ou rejeitado
     * @throws com.cernecommerce.core.domain.exception.compras.UnmatchedNfeLineException se
     *         alguma linha continuar sem SKU resolvido depois dos overrides
     */
    GoodsReceipt confirmImport(NfeImportConfirmCommand command, String username);

    /** SKU informado manualmente pelo operador para uma linha que o casamento automático não resolveu. */
    record LineOverride(int itemNumber, String sku) {
    }

    record NfeImportConfirmCommand(Long nfeImportId, String warehouseCode, List<LineOverride> overrides) {
    }
}
