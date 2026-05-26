package com.securityspring.core.ports.out.token;

import java.util.Set;

public interface AccessTokenPort {
    String generateFor(String username, Set<String> authorities);
}
