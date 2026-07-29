package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Comprovante interno de uma venda de balcão — <b>não é documento fiscal</b>. Resumo para a loja
 * imprimir/exportar hoje; a NFC-e (Fatia 11, `FIN-F002`) é quem substitui isto por um documento de
 * verdade, quando o emissor terceiro for integrado.
 */
@Data
public class SaleReceiptResponseDTO {

    private Long orderId;
    private String orderNumber;
    private String warehouseCode;
    private Instant concludedAt;

    @Schema(description = "Nulo em venda anônima de balcão.")
    private Long customerId;

    private List<SaleReceiptItemResponseDTO> items;

    private BigDecimal grossAmount;
    private BigDecimal discountAmount;
    private BigDecimal netAmount;

    @Schema(description = "Troco devolvido, se houve.")
    private BigDecimal changeAmount;

    private List<OrderPaymentResponseDTO> payments;
}
