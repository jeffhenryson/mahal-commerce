package com.cernecommerce.core.domain.model.auth;

public record OAuthLoginResult(TokenPair tokenPair, String username) {}
