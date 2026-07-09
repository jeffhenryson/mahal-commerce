package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleRequest {
    @NotBlank
    @Size(min = 3, max = 80)
    private String name;
}
