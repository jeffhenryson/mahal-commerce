package com.securityspring.adapter.out.security.credential;

import com.securityspring.core.ports.out.user.UserAuthoritiesPort;
import com.securityspring.core.ports.out.user.UserCachePort;
import com.securityspring.core.ports.out.user.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
public class JpaUserAuthoritiesAdapter implements UserAuthoritiesPort {

    private final UserRepository userRepository;

    public JpaUserAuthoritiesAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Cacheable(cacheNames = UserCachePort.USER_AUTHORITIES_CACHE, key = "#username")
    @Transactional(readOnly = true)
    @Override
    public Set<String> loadAuthoritiesByUsername(String username) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Set<String> authorities = new HashSet<>();
        user.getRoles().forEach(role -> {
            authorities.add(role.getName());
            role.getPermissions().forEach(perm -> authorities.add(perm.getName()));
        });
        return authorities;
    }
}
