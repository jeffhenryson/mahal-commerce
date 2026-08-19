package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Um item da lista de reposição (item 1 do pedido do frontend). Os campos de estoque
 * ({@code currentStock}, {@code minStock}, {@code suggestedQuantity}, {@code unitCost},
 * {@code previousPurchase}) são um snapshot tirado no momento do {@code POST} — deliberadamente
 * não recalculados na leitura. {@code quantity} e {@code note} são os dois únicos editáveis depois.
 */
@Data
public class ReplenishmentListItemResponseDTO {
    private String sku;
    private String productName;
    private String category;
    private String brand;
    private String unit;

    /** Saldo no momento da anotação. */
    private BigDecimal currentStock;

    /** Ponto de reposição no momento da anotação, ou nulo se o SKU não tinha um configurado. */
    private BigDecimal minStock;

    /** {@code max(0, minStock - currentStock)} no momento da anotação, ou nulo se {@code minStock} for nulo. */
    private BigDecimal suggestedQuantity;

    /** Quanto se pretende comprar — o único campo de quantidade editável via PATCH. */
    private BigDecimal quantity;

    /** {@code pricing.costPrice} (efetivo do SKU) no momento da anotação. */
    private BigDecimal unitCost;

    /** Nula quando o SKU nunca teve uma ENTRADA registrada neste depósito. */
    private PreviousPurchaseResponseDTO previousPurchase;

    private String note;
    private Instant createdAt;
    private String createdBy;

    @Data
    public static class PreviousPurchaseResponseDTO {
        private BigDecimal quantity;
        private BigDecimal unitCost;
        private Instant purchasedAt;
    }
}
