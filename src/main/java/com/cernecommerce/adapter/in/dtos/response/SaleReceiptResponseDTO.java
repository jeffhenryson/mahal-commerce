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
    private Instant createdAt;
    private Instant concludedAt;

    @Schema(description = "BALCAO, MESA ou MARKETPLACE.")
    private String channel;
    private String status;

    @Schema(description = "Rótulo da mesa, só em pedido de MESA.")
    private String tableLabel;
    private Long comandaId;

    @Schema(description = "Caixa (sessão de PDV) que liquidou o pedido; nulo em marketplace.")
    private Long sessionId;

    @Schema(description = "Operador do caixa que liquidou o pedido.")
    private String operatorName;

    @Schema(description = "Nulo em venda anônima de balcão.")
    private Long customerId;
    private String customerName;
    @Schema(description = "Telefone/contato do cliente no CRM.")
    private String customerPhone;
    @Schema(description = "CPF do cliente no CRM.")
    private String customerDocument;

    @Schema(description = "Entrega/retirada (PDV-F022); nulo sem entrega.")
    private DeliveryResponseDTO delivery;

    @Schema(description = "Cashback resgatado como desconto no pedido.")
    private BigDecimal cashbackRedeemed;

    private List<SaleReceiptItemResponseDTO> items;

    private BigDecimal grossAmount;
    private BigDecimal discountAmount;
    private BigDecimal netAmount;

    @Schema(description = "Troco devolvido, se houve.")
    private BigDecimal changeAmount;

    @Schema(description = "Taxa de serviço da mesa (PDV-F015). FORA do netAmount de propósito: o "
            + "líquido é a receita da mercadoria, a taxa é repasse ao garçom — somá-la ali inflaria "
            + "receita e margem. Zero em toda venda que não veio de mesa.")
    private BigDecimal serviceFeeAmount;

    @Schema(description = "PDV-F022 — taxa de entrega, já incluída em totalPayable; zero sem entrega.")
    private BigDecimal deliveryFee;

    @Schema(description = "O que o cliente efetivamente paga: netAmount + serviceFeeAmount + deliveryFee "
            + "(PDV-F015, PDV-F022). É contra este valor que o pagamento é validado e o troco calculado. "
            + "No balcão sem entrega coincide com netAmount.")
    private BigDecimal totalPayable;


    private List<OrderPaymentResponseDTO> payments;
}
