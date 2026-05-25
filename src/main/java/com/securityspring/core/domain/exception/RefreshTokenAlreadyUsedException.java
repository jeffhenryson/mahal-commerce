package com.securityspring.core.domain.exception;

public class RefreshTokenAlreadyUsedException extends RuntimeException {

    private final String username;

    public RefreshTokenAlreadyUsedException(String username) {
        super("Refresh token reuse detected for user: " + username);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
