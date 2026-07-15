package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 3, max = 80)
    private String username;

    @NotBlank
    @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
    @Pattern(regexp = PasswordPolicy.COMPLEXITY_REGEXP, message = PasswordPolicy.COMPLEXITY_MESSAGE)
    private String password;

    @NotBlank
    @Email(message = "Email inválido")
    @Size(max = 254)
    private String email;
}
