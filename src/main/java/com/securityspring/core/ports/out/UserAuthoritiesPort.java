package com.security_spring.core.ports.out;

import java.util.Set;

public interface UserAuthoritiesPort {
    Set<String> loadAuthoritiesByUsername(String username);
}
