package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 2º rosh de uma sessão (PDV-F021): nova essência, mesmos utensílios. */
@Data
public class AddRoshExtraRequest {

    @Schema(description = "Faixa da nova essência; nula usa a faixa da sessão.", example = "1")
    private Long tierId;

    @NotBlank
    @Size(max = 150)
    @Schema(example = "Sence Menta")
    private String essencia;
}
