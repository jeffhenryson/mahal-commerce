package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Marca do catálogo")
public class BrandRequest {

    @NotBlank
    @Size(max = 100)
    private String name;
}
