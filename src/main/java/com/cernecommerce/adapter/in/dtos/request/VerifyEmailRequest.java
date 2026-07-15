package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyEmailRequest {
    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{12}", message = "Code must be exactly 12 alphanumeric characters")
    private String code;
}
