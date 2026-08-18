package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class ComandaResponseDTO {

    private Long id;
    private Long sessionId;
    private String warehouseCode;
    private String tableOrCustomerLabel;
    private String status;
    private List<ComandaItemResponseDTO> items;

    @Schema(description = "Soma dos subtotais dos itens já lançados.")
    private BigDecimal runningTotal;

    @Schema(description = "Preenchido só depois do fechamento — o pedido gerado a partir dos itens.")
    private Long orderId;

    private String openedBy;
    private Instant openedAt;
    private Instant closedAt;
}
