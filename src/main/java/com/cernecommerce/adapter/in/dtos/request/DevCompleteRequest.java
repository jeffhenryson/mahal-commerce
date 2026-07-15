package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class DevCompleteRequest {

    @NotBlank(message = "Token de desafio DEV obrigatório")
    private String devToken;

    @NotBlank(message = "Código TOTP obrigatório")
    @Pattern(regexp = "\\d{6}", message = "O código deve ter exatamente 6 dígitos")
    private String totpCode;

    public String getDevToken() { return devToken; }
    public void setDevToken(String devToken) { this.devToken = devToken; }
    public String getTotpCode() { return totpCode; }
    public void setTotpCode(String totpCode) { this.totpCode = totpCode; }
}
