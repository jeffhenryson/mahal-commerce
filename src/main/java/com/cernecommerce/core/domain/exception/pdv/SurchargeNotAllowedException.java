package com.cernecommerce.core.domain.exception.pdv;

/**
 * Acréscimo lançado por quem não tem {@code PDV_COMANDA_SURCHARGE} (PDV-F011).
 *
 * <p>403 pela simetria com {@link CourtesyNotAllowedException}: se lançar uma linha a preço zero é
 * um desconto de 100% e tem dono, subir o preço à mão também tem. O valor é decidido no balcão,
 * caso a caso, sem tabela que o justifique depois — é o tipo de lançamento que precisa de um nome
 * atrás dele quando o fechamento não bater.</p>
 */
public class SurchargeNotAllowedException extends RuntimeException {
    public SurchargeNotAllowedException(String username) {
        super("Usuário " + username + " não tem permissão para lançar acréscimo na comanda");
    }
}
