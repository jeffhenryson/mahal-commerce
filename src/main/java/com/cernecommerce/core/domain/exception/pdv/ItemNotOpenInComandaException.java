package com.cernecommerce.core.domain.exception.pdv;

import java.util.List;

/**
 * O fechamento parcial (PDV-F017) recebeu id de linha que não está em aberto nesta comanda — ou não
 * é dela, ou já foi cobrada por um pedido anterior.
 *
 * <p>Distinta de {@code ComandaItemNotFoundException}, que é 404 de uma linha que não existe: aqui a
 * linha pode existir e estar paga, e a resposta certa para o operador é "essa já foi", não "não
 * achei".</p>
 */
public class ItemNotOpenInComandaException extends RuntimeException {

    public ItemNotOpenInComandaException(Long comandaId, List<Long> itemIds) {
        super("Linhas não abertas na comanda " + comandaId + ": " + itemIds
                + ". Ou não pertencem a ela, ou já foram cobradas por um pedido anterior.");
    }
}
