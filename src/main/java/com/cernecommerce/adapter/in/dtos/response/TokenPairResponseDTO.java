package com.cernecommerce.adapter.in.dtos.response;

import lombok.Getter;

@Getter
public class TokenPairResponseDTO {
    private final String accessToken;
    private final String refreshToken;
    private final String tokenType = "Bearer";
    private final long expiresIn;

    public TokenPairResponseDTO(String accessToken, String refreshToken, long expiresInSeconds) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresInSeconds;
    }
}
