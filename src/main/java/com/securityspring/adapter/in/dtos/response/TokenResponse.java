package com.security_spring.adapter.in.dtos.response;

public class TokenResponse {
    private String accessToken;
    private String tokenType = "Bearer";

    public TokenResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
}
