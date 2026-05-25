package com.securityspring.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyEmailRequest {
    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "O código deve ter exatamente 6 dígitos")
    private String code;
}
