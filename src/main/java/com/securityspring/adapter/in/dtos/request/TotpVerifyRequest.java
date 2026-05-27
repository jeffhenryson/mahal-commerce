package com.securityspring.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TotpVerifyRequest {
    @NotBlank
    private String challengeToken;
    @NotBlank
    private String code;
}
