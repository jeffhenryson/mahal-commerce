package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class ComandaItemResponseDTO {

    private Long id;
    private String sku;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal costPrice;
    private BigDecimal subtotal;
    private Instant addedAt;

    @Schema(description = "Por que a linha existe: NORMAL, OPEN_ROSH, SABOR_EXTRA ou TROCA "
            + "(PDV-F010). Sem isto a tela não sabe quais linhas aceitam troca de sabor.")
    // PDV-C010 — enum, não String: era assimétrico com AddComandaItemRequest.mode, que já é o
    // enum, e o cliente mantinha o tipo à mão em vez de gerá-lo do OpenAPI.
    private ConsumptionMode mode;

    @Schema(description = "Linha a preço zero que ainda baixou estoque. Campo próprio, não "
            + "inferido de unitPrice = 0 — um desconto de 100% dá o mesmo zero.")
    private boolean courtesy;

    @Schema(description = "Linha de origem nesta comanda. Preenchido em SABOR_EXTRA e TROCA.")
    private Long linkedItemId;

    @Schema(description = "Registro do setup da mesa — narguilé, filtro, qual pinça (PDV-F011). "
            + "Texto opaco, sem efeito em preço. Nulo quando nada foi registrado.")
    private String notes;

    @Schema(description = "Parcela de unitPrice que veio de acréscimo manual no open rosh "
            + "(PDV-F011). Campo próprio porque unitPrice já é a soma: sem ele não há como "
            + "separar o que era preço-base do que foi cobrado a mais. Nulo na maioria das linhas.")
    private BigDecimal surchargeAmount;

    /**
     * PDV-F017 — o pedido que já cobrou esta linha numa conta dividida. Nulo é a linha em aberto, e
     * é a soma delas que o {@code runningTotal} da comanda devolve.
     */
    @Schema(description = "Pedido que já cobrou esta linha (conta dividida, PDV-F017). Nulo = ainda "
            + "em aberto; o runningTotal da comanda soma apenas as linhas nulas.", example = "1042")
    private Long closedInOrderId;

    @Schema(description = "Qual uso da lata aberta esta linha foi, no instante do lançamento "
            + "(EST-F027) — com packageSessionsPerUnit, é o \"3 de 5\" da tela, sem uma segunda "
            + "chamada por linha. Nulo quando a linha baixou uma unidade inteira: produto que não "
            + "é vendido por sessão, ou sem sessionsPerUnit no cadastro.", example = "3")
    private Integer packageUses;

    @Schema(description = "Quantas sessões a lata desta linha rendia (EST-F027). Cópia do cadastro "
            + "no momento da abertura: editar sessionsPerUnit no catálogo não reescreve o "
            + "histórico.", example = "5")
    private Integer packageSessionsPerUnit;

    // PDV-F019 — kit montável. Nulos para linha avulsa; subtotal continua bruto.
    private String kitBundleId;
    private Long kitTemplateId;
    private BigDecimal kitDiscountAmount;
}
