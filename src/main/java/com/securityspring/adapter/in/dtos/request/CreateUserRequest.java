package com.securityspring.adapter.in.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateUserRequest {
    @NotBlank
    @Size(min = 3, max = 80)
    private String username;

    @NotBlank
    @Size(min = 8, max = 120)
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$",
        message = "A senha deve conter ao menos uma letra maiúscula, uma minúscula, um dígito e um caractere especial"
    )
    private String password;

    @Email(message = "Email inválido")
    @Size(max = 254)
    private String email;

    private List<String> roles = new ArrayList<>();
}
