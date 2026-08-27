package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetConfigRequest(
    @NotNull @Size(max = 255) String value
) {}
