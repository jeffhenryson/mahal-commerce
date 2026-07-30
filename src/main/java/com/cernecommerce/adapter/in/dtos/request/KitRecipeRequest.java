package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class KitRecipeRequest {
    @NotEmpty
    @Valid
    private List<KitComponentRequest> components;
}
