package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TotpConfirmRequest {
    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "Código TOTP deve ter 6 dígitos")
    private String code;
}
