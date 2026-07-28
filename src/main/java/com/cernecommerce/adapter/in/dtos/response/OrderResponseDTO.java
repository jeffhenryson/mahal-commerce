package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class OrderResponseDTO {

    private Long id;

    @Schema(description = "Numeração de sequência própria, emitida na conclusão. Pedidos "
            + "anteriores à V65 têm prefixo LEG-.", example = "000001000")
    private String orderNumber;

    @Schema(description = "BALCAO ou MARKETPLACE.")
    private String channel;

    private String status;

    @Schema(description = "Nulo em venda anônima de balcão.")
    private Long customerId;

    @Schema(description = "Nulo em pedido de marketplace.")
    private Long sessionId;

    private String warehouseCode;

    @Schema(description = "Bruto: soma de quantidade x preço unitário dos itens.")
    private BigDecimal grossAmount;

    private BigDecimal discountAmount;

    @Schema(description = "Cashback usado como abatimento. Sempre zero enquanto CRM-F003 não existir.")
    private BigDecimal cashbackRedeemed;

    @Schema(description = "Líquido a pagar: bruto - desconto - cashback resgatado.")
    private BigDecimal netAmount;

    @Schema(description = "Troco. Não é linha de pagamento.")
    private BigDecimal changeAmount;

    private String cancelReason;
    private Instant createdAt;
    private Instant paidAt;
    private Instant concludedAt;
    private Instant cancelledAt;

    private List<OrderItemResponseDTO> items;
}
