package com.securityspring.adapter.out.cache;

import com.securityspring.core.ports.out.user.UserCachePort;
import com.securityspring.infra.security.CustomUserDetailsService;
import org.springframework.stereotype.Component;

@Component
public class UserCacheAdapter implements UserCachePort {

    private final CustomUserDetailsService userDetailsService;

    public UserCacheAdapter(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public void evict(String username) {
        userDetailsService.evict(username);
    }
}
