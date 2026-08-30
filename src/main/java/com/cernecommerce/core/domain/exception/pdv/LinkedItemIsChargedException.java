package com.cernecommerce.core.domain.exception.pdv;

import java.util.List;

/**
 * Remoção barrada porque a linha tem {@code SABOR_EXTRA} pendurado nela (PDV-F012).
 *
 * <p>409, e a recusa é deliberada. A {@code TROCA} é arrastada junto porque é cortesia e não existe
 * sem a sessão que a originou; o {@code SABOR_EXTRA} é linha própria e <b>pode estar sendo
 * cobrada</b> — o segundo sabor de um duplo sem promo. Apagá-la em cascata tiraria dinheiro da conta
 * sem o operador ter pedido, e "removi o narguilé errado" não é o mesmo pedido que "tira o segundo
 * sabor da conta".</p>
 *
 * <p>A mensagem lista os ids justamente para o operador saber o que remover antes.</p>
 */
public class LinkedItemIsChargedException extends RuntimeException {

    public LinkedItemIsChargedException(Long itemId, List<Long> chargedChildren) {
        super("A linha " + itemId + " tem itens cobrados pendurados nela (" + chargedChildren
                + "): remova-os antes, para não tirar valor da conta sem querer");
    }
}
