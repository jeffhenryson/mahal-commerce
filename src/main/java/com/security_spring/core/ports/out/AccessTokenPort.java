package com.security_spring.core.ports.out;

import java.util.Set;

public interface AccessTokenPort {
    String generateFor(String username, Set<String> authorities);
}
