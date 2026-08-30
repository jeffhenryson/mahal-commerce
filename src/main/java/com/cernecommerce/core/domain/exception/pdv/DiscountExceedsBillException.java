package com.cernecommerce.core.domain.exception.pdv;

import java.math.BigDecimal;

/**
 * Desconto pedido maior que a própria conta da mesa (PDV-C016).
 *
 * <p>{@code DiscountProration.distribute} já recusava o caso — não há como ratear proporcionalmente
 * um abatimento maior que a soma das linhas sem violar a invariante de {@code OrderItem} —, mas
 * recusava com {@code IllegalArgumentException}, que o handler global transforma num <b>400
 * genérico</b> (`BAD_REQUEST`), descartando a mensagem.</p>
 *
 * <p><b>O problema maior era a ordem.</b> O rateio roda <b>antes</b> de
 * {@code requireDiscountWithinLimit}, então um desconto absurdo nunca chegava ao
 * {@code 409 DISCOUNT_LIMIT_EXCEEDED} — o código que a tela já sabe tratar, e que ela recebe para
 * um desconto de 11%. Pedir 11% de desconto dava um erro acionável; pedir o dobro da conta dava
 * "Requisição inválida".</p>
 *
 * <p>Checado <b>antes</b> do rateio, e não dentro dele: {@code DiscountProration} é função pura de
 * aritmética, e o vocabulário de erro do PDV não é dela.</p>
 */
public class DiscountExceedsBillException extends RuntimeException {

    public DiscountExceedsBillException(Long comandaId, BigDecimal discountAmount, BigDecimal billAmount) {
        super("Desconto de " + discountAmount + " passa do total da comanda #" + comandaId + " ("
                + billAmount + "). O abatimento é sobre a conta, não pode ser maior que ela.");
    }
}
