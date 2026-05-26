package com.securityspring.adapter.in.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {
    @NotBlank
    @Size(min = 3, max = 80)
    private String username;

    @Email(message = "Email inválido")
    @Size(max = 254)
    private String email;
}
