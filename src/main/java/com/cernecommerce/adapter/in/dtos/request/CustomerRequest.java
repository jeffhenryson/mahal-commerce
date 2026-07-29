package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * {@code contato} e {@code email} são opcionais desde CRM-C005 — só {@code nome} e ao menos um
 * identificador (cpf, email ou contato) são exigidos. Corpo sem nenhum dos três responde
 * {@code 400 BAD_REQUEST}, checado no compact constructor de {@code Customer}: Bean Validation não
 * expressa bem "pelo menos um destes campos" sem um validador próprio.
 */
@Data
public class CustomerRequest {
    @NotBlank
    @Size(max = 255)
    private String nome;

    @Size(max = 30)
    @Schema(description = "Telefone/contato. Opcional se email ou cpf forem informados.")
    private String contato;

    @Email
    @Size(max = 255)
    @Schema(description = "Opcional se contato ou cpf forem informados.")
    private String email;

    @Size(min = 11, max = 11)
    @Schema(description = "Identificador OFICIAL do cadastro. Sem CPF, o cliente é \"leve\": "
            + "válido e pesquisável, mas não elegível a cashback quando o programa existir.")
    private String cpf;

    @Size(max = 100)
    private String origem;
}
