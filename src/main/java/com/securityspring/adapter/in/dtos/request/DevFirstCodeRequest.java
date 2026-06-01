package com.securityspring.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class DevFirstCodeRequest {

    @NotBlank(message = "Código TOTP obrigatório")
    @Pattern(regexp = "\\d{6}", message = "O código deve ter exatamente 6 dígitos")
    private String totpCode;

    public String getTotpCode() { return totpCode; }
    public void setTotpCode(String totpCode) { this.totpCode = totpCode; }
}
