package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductAttributeRequest {
    @NotBlank
    @Size(max = 50)
    private String type;

    @NotBlank
    @Size(max = 100)
    private String value;
}
