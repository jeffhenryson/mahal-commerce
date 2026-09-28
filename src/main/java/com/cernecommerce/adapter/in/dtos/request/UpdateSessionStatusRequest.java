package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pdv.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** PDV-F023 — novo status de uma sessão da mesa. */
@Data
public class UpdateSessionStatusRequest {

    @NotNull
    @Schema(description = "PREPARANDO, ENTREGUE ou RECOLHIDO.", example = "ENTREGUE")
    private SessionStatus status;
}
