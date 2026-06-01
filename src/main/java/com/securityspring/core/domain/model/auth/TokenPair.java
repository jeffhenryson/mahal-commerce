package com.securityspring.core.domain.model.auth;

public class TokenPair {

    private final String accessToken;
    private final String refreshToken;
    private final String username;

    public TokenPair(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, null);
    }

    public TokenPair(String accessToken, String refreshToken, String username) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.username = username;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getUsername() {
        return username;
    }
}
