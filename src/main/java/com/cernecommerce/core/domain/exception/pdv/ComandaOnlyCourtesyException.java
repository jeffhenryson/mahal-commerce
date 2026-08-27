package com.cernecommerce.core.domain.exception.pdv;

/**
 * Fechamento de comanda cujo total é zero porque <b>todas</b> as linhas são cortesia.
 *
 * <p>Decisão explícita do dono, não efeito colateral: pelo desenho da feature a cortesia é sempre
 * acessória de uma sessão paga — o segundo sabor de um duplo, a troca durante um open rosh — então
 * uma mesa só de cortesias quase sempre é erro de lançamento, e fechá-la geraria um pedido
 * concluído de R$ 0 que ninguém revisaria.</p>
 *
 * <p>Irmã de {@link ComandaEmptyException}, que cobre o caso vizinho: comanda sem linha nenhuma.
 * As duas juntas fecham a porta do pedido de valor zero.</p>
 */
public class ComandaOnlyCourtesyException extends RuntimeException {
    public ComandaOnlyCourtesyException(Long comandaId) {
        super("Comanda " + comandaId + " só tem linhas de cortesia — não há valor a cobrar");
    }
}
