package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerNoteRequest {
    @NotBlank
    @Size(max = 2000)
    private String texto;
}
