package com.securityspring.adapter.in.dtos.response;

import lombok.Getter;

@Getter
public class TokenPairResponseDTO {
    private final String accessToken;
    private final String refreshToken;
    private final String tokenType = "Bearer";

    public TokenPairResponseDTO(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
