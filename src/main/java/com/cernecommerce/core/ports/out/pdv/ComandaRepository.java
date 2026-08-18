package com.cernecommerce.core.ports.out.pdv;

import com.cernecommerce.core.domain.model.pdv.Comanda;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de comandas de mesa (PDV-F009).
 */
public interface ComandaRepository {

    Optional<Comanda> findById(Long id);

    /** Comandas {@code ABERTA} de uma sessão — a lista de "mesas ocupadas" do caixa. */
    List<Comanda> findOpenBySessionId(Long sessionId);

    Comanda save(Comanda comanda);
}
