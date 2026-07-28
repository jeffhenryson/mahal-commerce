package com.cernecommerce.core.domain.exception.estoque;

import java.math.BigDecimal;

/**
 * A operação seria possível pelo saldo <b>físico</b>, mas esbarra na parte dele que está
 * <b>reservada</b> para um pedido ainda não concluído (EST-F021).
 *
 * <p>Distinta de {@link InsufficientStockException} de propósito. "Não tem" e "tem, mas está
 * separado para um pedido online" pedem ações diferentes de quem está no balcão: a segunda tem
 * solução — cancelar a reserva pelo painel e vender — e a primeira não. Um erro só, com a mesma
 * mensagem, esconderia a diferença justamente na hora em que ela decide o que o operador faz.</p>
 *
 * <p>Também cobre o {@code AJUSTE} de inventário que levaria o saldo abaixo do reservado: a
 * contagem encontrou menos unidades do que já foram prometidas, e quais pedidos perder é decisão
 * humana.</p>
 */
public class ReservedStockException extends RuntimeException {

    public ReservedStockException(String sku, Long warehouseId, BigDecimal quantity,
            BigDecimal reservedQuantity, BigDecimal requestedQuantity) {
        super("Saldo reservado impede a operação para o SKU " + sku + " no depósito " + warehouseId
                + ": saldo físico " + quantity + ", reservado " + reservedQuantity
                + ", disponível " + quantity.subtract(reservedQuantity)
                + ", quantidade solicitada " + requestedQuantity);
    }
}
