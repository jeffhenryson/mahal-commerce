package com.securityspring.core.domain.model.auth;

public record LoginResponse(
        TokenPair tokenPair,
        String challengeToken,
        boolean twoFactorRequired) {

    public static LoginResponse success(TokenPair pair) {
        return new LoginResponse(pair, null, false);
    }

    public static LoginResponse twoFactorChallenge(String challengeToken) {
        return new LoginResponse(null, challengeToken, true);
    }
}
