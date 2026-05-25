package com.security_spring.adapter.in.dtos.response;

public class TokenPairResponse {
    private final String accessToken;
    private final String refreshToken;
    private final String tokenType = "Bearer";

    public TokenPairResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getTokenType() { return tokenType; }
}
