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

    @Schema(description = "Nome do cliente, resolvido a partir de customerId. Nulo se o pedido "
            + "não tiver cliente vinculado (venda anônima de balcão).")
    private String customerName;

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

    @Schema(description = "Instante da reserva para retirada depois (PDV-F008), quando status é ou "
            + "já foi RESERVADO. Permanece preenchido após a retirada (RESERVADO -> CONCLUIDO).")
    private Instant reservedAt;

    @Schema(description = "Instante em que o pedido foi separado (status SEPARADO), quando aplicável.")
    private Instant separatedAt;

    @Schema(description = "Instante em que o pedido saiu para entrega (status ENVIADO), quando aplicável.")
    private Instant shippedAt;

    @Schema(description = "Instante em que o pedido foi entregue (status ENTREGUE), quando aplicável.")
    private Instant deliveredAt;

    @Schema(description = "Estados para os quais este pedido pode transitar agora.")
    private List<String> allowedTransitions;

    private List<OrderItemAdminResponseDTO> items;
}
