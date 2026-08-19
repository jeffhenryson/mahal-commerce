package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Tipo de atributo do catálogo (ex.: Sabor, Aroma, Potência/Voltagem)")
public class AttributeTypeRequest {

    @NotBlank
    @Size(max = 50)
    private String name;
}
