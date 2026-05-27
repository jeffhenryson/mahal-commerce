package com.securityspring.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TotpDisableRequest {
    @NotBlank
    private String currentPassword;
    @NotBlank
    private String code;
}
