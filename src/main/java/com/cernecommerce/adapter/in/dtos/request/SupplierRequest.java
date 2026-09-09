package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Cadastro de fornecedor (COM-F001). */
@Data
public class SupplierRequest {

    @NotBlank
    @Size(max = 150)
    @Schema(description = "Razão social", example = "Distribuidora Zomo LTDA",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String legalName;

    // Aceita com ou sem máscara: o domínio normaliza para só dígitos antes de gravar e antes de
    // conferir duplicidade. O @Size cobre a máscara mais longa de CNPJ (18 caracteres).
    @NotBlank
    @Size(min = 11, max = 18)
    @Schema(description = "CNPJ (14 dígitos) ou CPF (11) — com ou sem máscara, o servidor "
            + "normaliza. CPF é aceito porque produtor rural que emite nota é pessoa física.",
            example = "12.345.678/0001-99", requiredMode = Schema.RequiredMode.REQUIRED)
    private String taxId;

    @Email
    @Size(max = 150)
    @Schema(description = "E-mail de contato (opcional)", example = "contato@zomo.com.br")
    private String email;
}
