package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Autocadastro do cliente do marketplace (ECM-F001). Sem campo de username: username = email
 * internamente, o cliente só pensa em email/senha, como qualquer loja online.
 */
@Data
public class ShopRegisterRequest {

    @NotBlank
    @Size(max = 255)
    private String nome;

    @NotBlank
    @Email(message = "Email inválido")
    @Size(max = 254)
    private String email;

    @Size(max = 30)
    @Schema(description = "Telefone/contato. Opcional.")
    private String contato;

    @NotBlank
    @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
    @Pattern(regexp = PasswordPolicy.COMPLEXITY_REGEXP, message = PasswordPolicy.COMPLEXITY_MESSAGE)
    private String password;
}
