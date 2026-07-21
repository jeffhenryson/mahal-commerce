package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerRequest {
    @NotBlank
    @Size(max = 255)
    private String nome;

    @NotBlank
    @Size(max = 30)
    private String contato;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Size(min = 11, max = 11)
    private String cpf;

    @Size(max = 100)
    private String origem;
}
