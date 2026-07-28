package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Pedido na visão do administrador — inclui custo e margem por item. */
@Data
public class OrderAdminResponseDTO {

    private Long id;
    private String orderNumber;

    @Schema(description = "Origem do pedido: BALCAO ou MARKETPLACE. Imutável — não muda quando um "
            + "pedido do app é pago no balcão.")
    private String channel;

    private String status;
    private Long customerId;

    @Schema(description = "Caixa que liquidou o pedido. Presente em toda venda de balcão e também "
            + "em pedido do app pago na loja.")
    private Long sessionId;

    private String warehouseCode;
    private BigDecimal grossAmount;
    private BigDecimal discountAmount;
    private BigDecimal cashbackRedeemed;
    private BigDecimal netAmount;
    private BigDecimal changeAmount;

    @Schema(description = "Soma da margem dos itens. Nula se algum item não tiver custo congelado.")
    private BigDecimal marginAmount;

    private String cancelReason;
    private Instant createdAt;
    private Instant paidAt;
    private Instant concludedAt;
    private Instant cancelledAt;

    @Schema(description = "Estados para os quais este pedido pode transitar agora.")
    private List<String> allowedTransitions;

    private List<OrderItemAdminResponseDTO> items;
}
