package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.compras.Supplier;

/**
 * Port de entrada do domínio <b>compras</b>.
 *
 * <p>Stub — expõe apenas uma leitura. Casos de uso previstos (TODO):
 * {@code registerSupplier}, {@code createPurchaseOrder}, {@code receiveGoods}
 * (entrada de mercadoria → movimenta estoque).</p>
 */
public interface ComprasUseCase {

    /** Lista os fornecedores paginados. Stub: retorna página vazia até a implementação. */
    PageResult<Supplier> listSuppliers(int page, int size);
}
