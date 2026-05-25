package com.securityspring.core.ports.out;

import java.util.Set;

public interface CredentialVerifierPort {

    record VerifiedUser(String username, Set<String> authorities) {}

    VerifiedUser verify(String username, String password);
}
