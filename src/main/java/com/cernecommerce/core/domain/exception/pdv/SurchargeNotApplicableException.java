package com.cernecommerce.core.domain.exception.pdv;

/**
 * Acréscimo pedido fora de {@code OPEN_ROSH} (PDV-F011).
 *
 * <p>Nos demais modos a diferença do sabor caro já está no {@code pricing} da própria variante, que
 * é onde ela deve morar — aceitar o acréscimo ali criaria dois lugares para o mesmo preço, e o
 * relatório deixaria de saber qual dos dois responde "quanto custa este sabor".</p>
 */
public class SurchargeNotApplicableException extends RuntimeException {
    public SurchargeNotApplicableException(String mode) {
        super("Acréscimo só é aceito em mode = OPEN_ROSH, e a linha veio como " + mode
                + ": nos demais modos o preço do sabor mora no pricing da variante");
    }
}
