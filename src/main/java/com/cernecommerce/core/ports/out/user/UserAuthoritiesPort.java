package com.cernecommerce.core.ports.out.user;

import java.util.Set;

public interface UserAuthoritiesPort {
    Set<String> loadAuthoritiesByUsername(String username);
}
