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
 * <p>Casos de uso previstos além dos atuais (TODO): {@code registerSupplier},
 * {@code createPurchaseOrder}.</p>
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
}
