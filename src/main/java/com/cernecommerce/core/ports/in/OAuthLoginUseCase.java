package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.auth.OAuthLoginResult;

public interface OAuthLoginUseCase {
    OAuthLoginResult loginWithGoogle(String idToken);
}
