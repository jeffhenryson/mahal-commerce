package com.security_spring.core.domain.model;

public class TokenPair {

    private final String accessToken;
    private final String refreshToken;

    public TokenPair(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
