package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "idToken é obrigatório")
        String idToken
) {}
