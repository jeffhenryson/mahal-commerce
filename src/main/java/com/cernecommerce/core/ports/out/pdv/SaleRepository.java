package com.cernecommerce.core.ports.out.pdv;

import com.cernecommerce.core.domain.model.pdv.Sale;

/**
 * Port de saída para persistência de vendas de balcão do PDV.
 */
public interface SaleRepository {

    Sale save(Sale sale);
}
