package com.cernecommerce.core.domain.exception.pdv;

/**
 * {@code TROCA} apontando para uma linha que não é {@code OPEN_ROSH}.
 *
 * <p>409, e não 400: o {@code linkedItemId} existe e é uma linha válida da comanda — o que impede a
 * troca é o <b>estado</b> daquela linha. Trocas ilimitadas são a contrapartida do valor fixo do
 * consumo livre; permitir troca cortesia sobre uma sessão comum daria narguilé de graça.</p>
 */
public class NotAnOpenRoshException extends RuntimeException {
    public NotAnOpenRoshException(Long linkedItemId, String mode) {
        super("linkedItemId " + linkedItemId + " é uma linha " + mode
                + " — só se troca sabor de uma linha OPEN_ROSH");
    }
}
