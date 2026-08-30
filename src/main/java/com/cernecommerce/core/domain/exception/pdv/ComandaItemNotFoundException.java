package com.cernecommerce.core.domain.exception.pdv;

/**
 * Linha inexistente, ou de outra mesa, na remoção de item (PDV-F012).
 *
 * <p>404 e não 403: um id de linha válido em <b>outra</b> comanda é indistinguível de um id que não
 * existe, do ponto de vista de quem opera esta mesa. Mesma escolha de
 * {@code LinkedItemRequiredException}, que também recusa a linha de origem que não está nesta
 * comanda.</p>
 */
public class ComandaItemNotFoundException extends RuntimeException {
    public ComandaItemNotFoundException(Long itemId, Long comandaId) {
        super("Item " + itemId + " não encontrado na comanda " + comandaId);
    }
}
