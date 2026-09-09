package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.GoodsReceiptItem;
import com.cernecommerce.core.domain.model.compras.Supplier;

import java.util.List;
import java.util.Optional;

/**
 * Port de entrada do domínio <b>compras</b>.
 *
 * <p>Caso de uso previsto além dos atuais (TODO): {@code createPurchaseOrder} (COM-F002), que
 * fecharia o ciclo pedido → recebimento — hoje {@link #receiveGoods} registra entrada de
 * mercadoria sem referenciar um pedido de compra formal.</p>
 */
public interface ComprasUseCase {

    /** Lista os fornecedores paginados. */
    PageResult<Supplier> listSuppliers(int page, int size);

    /**
     * Registra o recebimento de mercadoria de um fornecedor e dá entrada automática no
     * estoque do depósito informado (uma {@code StockMovement} de ENTRADA por item, via
     * {@code EstoqueUseCase.adjustStock}). Lança
     * {@link com.cernecommerce.core.domain.exception.compras.SupplierNotFoundException} se o
     * fornecedor não existir, ou
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException} se o
     * código do depósito não existir — nesses casos a transação inteira é revertida.
     */
    GoodsReceipt receiveGoods(Long supplierId, String warehouseCode, List<GoodsReceiptItem> items, String username);

    /**
     * Busca um recebimento por id — usado pelo enriquecimento do histórico de compras
     * ({@code EstoqueController.listPurchaseHistory}, item 2) para resolver o fornecedor a partir
     * do {@code goodsReceiptId} de uma {@code StockMovement}.
     */
    Optional<GoodsReceipt> findGoodsReceiptById(Long id);

    /** Busca um fornecedor por id — mesmo uso de {@link #findGoodsReceiptById}. */
    Optional<Supplier> findSupplierById(Long id);

    /**
     * Cadastra um fornecedor (COM-F001).
     *
     * <p>Era o pedido nº 1 deste domínio, e travava uma feature <b>já entregue</b>: a importação de
     * NF-e por XML (EST-F005) responde {@code 404 SUPPLIER_NOT_FOUND_BY_TAX_ID} quando o CNPJ do
     * emitente não está cadastrado — decisão deliberada, porque {@code taxId} é dado de compliance
     * e não se cria fornecedor por dedução —, e não havia nenhum caminho pela UI para cadastrá-lo.
     * O único jeito era {@code INSERT} direto no banco.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.compras.DuplicateSupplierTaxIdException se
     *         já existir fornecedor com o mesmo CNPJ/CPF, comparado <b>já normalizado</b>: com e
     *         sem máscara são o mesmo fornecedor.
     */
    Supplier registerSupplier(String legalName, String taxId, String email);

    /**
     * Edição parcial do cadastro — campo nulo mantém o valor atual, mesma semântica de
     * {@code EstoqueUseCase.updateProduct}.
     *
     * <p>{@code taxId} fica <b>fora</b> da edição, pelo mesmo motivo que o SKU do produto fica:
     * ele é a chave pela qual a importação de NF-e encontra o fornecedor, e trocá-lo faria os
     * recebimentos já registrados apontarem para um CNPJ que nunca os emitiu. Fornecedor com CNPJ
     * errado se resolve criando o certo e desativando o outro.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.compras.SupplierNotFoundException se o id
     *         não existir.
     */
    Supplier updateSupplier(Long id, String legalName, String email);

    /**
     * Ativa ou desativa o fornecedor. Endpoint próprio, e não um campo do PATCH, pelo mesmo motivo
     * de {@code PATCH /estoque/products/{sku}/active} (EST-F018): gera evento de auditoria
     * distinto de uma correção de nome.
     */
    Supplier setSupplierActive(Long id, boolean active);
}
