package com.cernecommerce.core.ports.out.compras;

import com.cernecommerce.core.domain.model.compras.GoodsReceipt;

import java.util.Optional;

/**
 * Port de saída para persistência de recebimentos de mercadoria.
 */
public interface GoodsReceiptRepository {

    GoodsReceipt save(GoodsReceipt receipt);

    Optional<GoodsReceipt> findById(Long id);
}
