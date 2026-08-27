package com.cernecommerce.infra.security;

import com.cernecommerce.adapter.out.cache.UserCacheAdapter;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Carrega UserDetails do banco e armazena em cache pelo username.
 *
 * <p>A eviction do cache é feita por {@link UserCacheAdapter} via {@link org.springframework.cache.CacheManager},
 * sem que este serviço precise expor um método de evict — eliminando o acoplamento
 * adapter → infra que existia anteriormente.</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Cacheable(cacheNames = UserCacheAdapter.CACHE_NAME, key = "#username")
    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        user.getRoles().forEach(role -> {
            authorities.add(new SimpleGrantedAuthority(role.getName()));
            role.getPermissions().forEach(perm ->
                    authorities.add(new SimpleGrantedAuthority(perm.getName())));
        });

        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .disabled(!user.isEnabled())
                .authorities(authorities)
                .build();
    }
}
