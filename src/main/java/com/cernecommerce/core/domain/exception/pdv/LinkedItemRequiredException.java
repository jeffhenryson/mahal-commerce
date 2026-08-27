package com.cernecommerce.core.domain.exception.pdv;

/**
 * {@code SABOR_EXTRA} ou {@code TROCA} sem uma linha de origem válida na mesma comanda.
 *
 * <p>O vínculo é o que deixa o consumo por sabor legível no pedido: sem ele, um segundo sabor vira
 * uma linha solta que ninguém consegue ligar à sessão que a originou.</p>
 */
public class LinkedItemRequiredException extends RuntimeException {
    public LinkedItemRequiredException(String mode, Long linkedItemId, Long comandaId) {
        super(linkedItemId == null
                ? "modo " + mode + " exige linkedItemId apontando para a linha de origem"
                : "linkedItemId " + linkedItemId + " não é uma linha da comanda " + comandaId);
    }
}
