package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.compras.Supplier;

import java.util.List;

/**
 * Port de entrada do domínio <b>compras</b>.
 *
 * <p>Stub — expõe apenas uma leitura. Casos de uso previstos (TODO):
 * {@code registerSupplier}, {@code createPurchaseOrder}, {@code receiveGoods}
 * (entrada de mercadoria → movimenta estoque).</p>
 */
public interface ComprasUseCase {

    /** Lista os fornecedores. Stub: retorna lista vazia até a implementação. */
    List<Supplier> listSuppliers();
}
