package com.securityspring.core.ports.out.oauth;

import com.securityspring.core.domain.model.auth.GoogleUserInfo;

public interface GoogleTokenVerifierPort {
    GoogleUserInfo verify(String idToken);
}
