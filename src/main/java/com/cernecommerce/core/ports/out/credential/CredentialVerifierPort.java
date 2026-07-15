package com.cernecommerce.core.ports.out.credential;

import java.util.Set;

public interface CredentialVerifierPort {

    record VerifiedUser(String username, Set<String> authorities) {}

    VerifiedUser verify(String username, String password);
}
