package com.securityspring.adapter.in.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendVerificationRequest {
    @NotBlank
    @Email(message = "Email inválido")
    private String email;
}
