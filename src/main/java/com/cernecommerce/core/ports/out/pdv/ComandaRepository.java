package com.cernecommerce.core.ports.out.pdv;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.Comanda;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de comandas de mesa (PDV-F009).
 */
public interface ComandaRepository {

    Optional<Comanda> findById(Long id);

    /**
     * Igual a {@link #findById}, mas travando a linha da comanda até o fim da transação (PDV-C008).
     *
     * <p>É o caminho obrigatório de <b>toda mutação</b> de comanda. Sem a trava, dois atendentes
     * operando a mesma mesa — o cenário que PDV-F010 liberou de propósito — leem o mesmo estado e
     * decidem sobre ele em paralelo: o {@code requireOpen} de um passa sobre uma comanda que o
     * outro já fechou, e o item entra numa mesa cujo pedido já foi gerado e pago. O estoque sai, e
     * ninguém é cobrado.</p>
     *
     * <p><b>Por que não {@code @Version}:</b> a versão otimista só protege quando o UPDATE chega a
     * ser emitido, e aqui ele não chega — o Hibernate compara o agregado com o <i>snapshot que ele
     * mesmo carregou</i>, não com o banco, então gravar de volta um estado velho não conta como
     * alteração e nenhuma colisão é detectada. A trava pessimista resolve na leitura, que é onde a
     * decisão é tomada. Molde no repositório: {@code RefreshTokenJpaRepository.findByTokenHashForUpdate}.</p>
     */
    Optional<Comanda> findByIdForUpdate(Long id);

    /**
     * Comandas {@code ABERTA} — as "mesas ocupadas" (PDV-C007).
     *
     * <p>Os dois filtros são <b>opcionais</b>: sem {@code sessionId} a consulta devolve as mesas da
     * loja inteira, que é a decisão do dono (<i>caixa por atendente, mesas compartilhadas</i>) e o
     * que substitui o merge N+1 que o cliente fazia — uma chamada por sessão aberta.</p>
     *
     * <p>Não há filtro por status da sessão de caixa, e isso é deliberado: desde <b>PDV-C005</b> o
     * caixa não fecha com mesa aberta, então comanda {@code ABERTA} já implica sessão {@code OPEN}.
     * Um join com {@code cash_register_session} só repetiria uma invariante que o módulo já
     * garante.</p>
     */
    PageResult<Comanda> findOpen(Long sessionId, String warehouseCode, int page, int size);

    /**
     * Ids das mesas {@code ABERTA} de uma sessão — a guarda de fechamento de caixa (PDV-C005).
     *
     * <p>Não é {@link #findOpen} com filtro: aquela é paginada, e uma guarda que enxerga só uma
     * página deixaria passar o fechamento de um caixa com mais mesas que a página. Devolve id
     * porque é tudo que a exceção precisa mostrar ao operador.</p>
     */
    List<Long> findOpenIdsBySessionId(Long sessionId);

    Comanda save(Comanda comanda);
}
