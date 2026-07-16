package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WarehouseRequest {
    @NotBlank
    @Size(min = 2, max = 50)
    private String code;

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    private String type;
}
