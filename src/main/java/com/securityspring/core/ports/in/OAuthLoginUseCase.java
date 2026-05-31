package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.auth.OAuthLoginResult;

public interface OAuthLoginUseCase {
    OAuthLoginResult loginWithGoogle(String idToken);
}
