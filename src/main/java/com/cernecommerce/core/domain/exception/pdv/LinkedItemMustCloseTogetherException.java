package com.cernecommerce.core.domain.exception.pdv;

import java.util.List;

/**
 * Uma seleção de conta dividida (PDV-F017) separaria linhas que estão amarradas por
 * {@code linked_item_id} — um {@code OPEN_ROSH} e as {@code TROCA}/{@code SABOR_EXTRA} dele.
 *
 * <p>Deixar as duas pontas em contas diferentes quebraria a leitura do consumo: a troca de sabor não
 * tem existência própria, é a troca <i>daquela</i> sessão, e um sabor extra é o segundo sabor
 * <i>daquele</i> narguilé. Em pedidos separados, cada metade descreve algo que não aconteceu — e a
 * margem do open rosh, que é a pergunta de negócio por trás da feature, sai partida ao meio.</p>
 *
 * <p>Mesma família de {@code LinkedItemIsChargedException} (PDV-F012), pelo mesmo motivo: o vínculo
 * entre linhas não sobrevive a elas seguirem caminhos diferentes.</p>
 */
public class LinkedItemMustCloseTogetherException extends RuntimeException {

    public LinkedItemMustCloseTogetherException(Long comandaId, List<Long> missingItemIds) {
        super("A seleção separaria linhas ligadas na comanda " + comandaId
                + ": as linhas " + missingItemIds + " precisam entrar no mesmo fechamento.");
    }
}
