package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class NfeImportResponseDTO {

    private Long id;

    @Schema(description = "Nulo quando status=REJECTED — CNPJ do emitente não encontrado.")
    private Long supplierId;

    private String emitterCnpj;
    private String warehouseCode;
    private String status;
    private Long goodsReceiptId;
    private List<NfeImportLineResponseDTO> lines;
    private String uploadedBy;
    private Instant uploadedAt;
    private Instant confirmedAt;
}
