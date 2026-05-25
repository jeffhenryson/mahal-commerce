package com.security_spring.infra.security;

import java.util.HashSet;
import java.util.Set;
import com.security_spring.core.ports.out.UserAuthoritiesPort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.security_spring.adapter.out.repository.UserJpaRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService, UserAuthoritiesPort {

    private final UserJpaRepository userRepo;

    public CustomUserDetailsService(UserJpaRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userRepo.findByUsernameWithRoles(username)
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

    @Transactional(readOnly = true)
    @Override
    public Set<String> loadAuthoritiesByUsername(String username) {
        var user = userRepo.findByUsernameWithRoles(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Set<String> authorities = new HashSet<>();
        user.getRoles().forEach(role -> {
            authorities.add(role.getName());
            role.getPermissions().forEach(perm -> authorities.add(perm.getName()));
        });
        return authorities;
    }
}
