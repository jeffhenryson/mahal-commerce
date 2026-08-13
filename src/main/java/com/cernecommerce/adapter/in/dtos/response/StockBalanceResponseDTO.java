package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Saldo de um SKU em um depósito.
 *
 * <p>Desde EST-F021 os três números são expostos juntos porque respondem a perguntas diferentes:
 * {@code quantity} é o que a prateleira tem (conferência de inventário), {@code reservedQuantity} é
 * o que está prometido a pedido não concluído, e {@code availableQuantity} é o que se pode vender —
 * é este último que a vitrine e o balcão devem consumir.</p>
 */
@Data
public class StockBalanceResponseDTO {
    private String sku;
    private String warehouseCode;
    /** Saldo físico na prateleira. */
    private BigDecimal quantity;
    /** Parte do físico já prometida a um pedido que ainda não saiu. */
    private BigDecimal reservedQuantity;
    /** {@code quantity - reservedQuantity}. O número que decide se dá para vender. */
    private BigDecimal availableQuantity;
    /** Unidade de medida do produto dono do saldo. Nula quando o SKU não resolve mais a um produto. */
    private String unit;
}
