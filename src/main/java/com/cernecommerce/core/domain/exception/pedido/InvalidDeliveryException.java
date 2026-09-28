package com.cernecommerce.core.domain.exception.pedido;

/**
 * Dados de entrega inconsistentes (PDV-F022) — endereço incompleto numa ENTREGA, endereço numa
 * RETIRADA, taxa alterada depois da venda. Exceção tipada para a mensagem chegar ao operador: o
 * handler de {@code IllegalArgumentException} a descartaria num 400 genérico.
 */
public class InvalidDeliveryException extends RuntimeException {

    public InvalidDeliveryException(String message) {
        super(message);
    }
}
