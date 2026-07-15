package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class TotpReplaceRequest {

    @NotBlank(message = "Código TOTP atual obrigatório")
    @Pattern(regexp = "\\d{6}", message = "O código deve ter exatamente 6 dígitos")
    private String currentTotpCode;

    public String getCurrentTotpCode() { return currentTotpCode; }
    public void setCurrentTotpCode(String currentTotpCode) { this.currentTotpCode = currentTotpCode; }
}
