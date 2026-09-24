package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Set;

/** Configuração do cardápio de sessão (PDV-F021). */
@Data
public class SessionSettingsRequest {

    @Size(max = 30)
    @Schema(example = "VASO_P")
    private String vasoPadraoCodigo;

    @Size(max = 30)
    @Schema(example = "VASO_G")
    private String vasoGrandeCodigo;

    @NotNull
    @DecimalMin("0.00")
    @Schema(example = "10.00")
    private BigDecimal upgradeVasoGrandePreco;

    @Schema(description = "Dias em que o 2º rosh sai de graça.", example = "[\"WEDNESDAY\"]")
    private Set<DayOfWeek> diasDuploRosh;
}
