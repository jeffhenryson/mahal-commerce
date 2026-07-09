package com.cernecommerce.adapter.in.dtos.request;

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
    @Size(min = 1, max = 254)
    private String email;

    /** Obrigatório apenas ao trocar o próprio email via PATCH /users/me. Ignorado em atualizações administrativas. */
    private String currentPassword;
}
