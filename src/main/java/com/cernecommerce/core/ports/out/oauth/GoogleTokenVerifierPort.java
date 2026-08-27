package com.cernecommerce.core.ports.out.oauth;

import com.cernecommerce.core.domain.model.auth.GoogleUserInfo;

public interface GoogleTokenVerifierPort {
    GoogleUserInfo verify(String idToken);
}
