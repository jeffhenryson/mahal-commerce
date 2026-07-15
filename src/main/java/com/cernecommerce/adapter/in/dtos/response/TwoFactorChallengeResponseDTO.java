package com.cernecommerce.adapter.in.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TwoFactorChallengeResponseDTO {
    private String status;
    private String challengeToken;
    private long expiresInSeconds;
}
