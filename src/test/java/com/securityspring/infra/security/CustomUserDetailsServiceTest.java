package com.securityspring.infra.security;

import com.securityspring.core.domain.model.rbac.Permission;
import com.securityspring.core.domain.model.rbac.Role;
import com.securityspring.core.domain.model.auth.User;
import com.securityspring.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Testa {@link CustomUserDetailsService} de forma unitária — sem Spring context.
 *
 * <p>O comportamento de {@code @Cacheable} não é verificado aqui (requer proxy Spring);
 * estes testes cobrem a lógica de carregamento e conversão de authorities.</p>
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository);
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void loadUserByUsername_returnsUserDetails_forExistingUser() {
        User user = User.fromPersisted(1L, "alice", "hashed", true, "alice@test.com", true, null, null, null, Set.of(), null, null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("alice");

        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getPassword()).isEqualTo("hashed");
        assertThat(result.isEnabled()).isTrue();
    }

    // ── authorities ──────────────────────────────────────────────────────────

    @Test
    void loadUserByUsername_buildsRolesAndPermissionsAsAuthorities() {
        Permission perm = Permission.of(1L, "USER_READ");
        Role role = Role.of(1L, "ROLE_USER", Set.of(perm));
        User user = User.fromPersisted(1L, "bob", "pwd", true, null, false, null, null, null, Set.of(role), null, null);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("bob");

        var authorityNames = result.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .toList();
        assertThat(authorityNames).containsExactlyInAnyOrder("ROLE_USER", "USER_READ");
    }

    @Test
    void loadUserByUsername_multipleRolesAndPermissions_allPresent() {
        Permission read  = Permission.of(1L, "USER_READ");
        Permission write = Permission.of(2L, "USER_WRITE");
        Role user  = Role.of(1L, "ROLE_USER",  Set.of(read));
        Role admin = Role.of(2L, "ROLE_ADMIN", Set.of(write));
        User domainUser = User.fromPersisted(1L, "carol", "pwd", true, null, false, null, null, null, Set.of(user, admin), null, null);
        when(userRepository.findByUsername("carol")).thenReturn(Optional.of(domainUser));

        UserDetails result = service.loadUserByUsername("carol");

        var names = result.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        assertThat(names).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN", "USER_READ", "USER_WRITE");
    }

    // ── conta desabilitada ────────────────────────────────────────────────────

    @Test
    void loadUserByUsername_setsDisabled_whenUserNotEnabled() {
        User disabled = User.fromPersisted(2L, "dave", "pwd", false, null, false, null, null, null, Set.of(), null, null);
        when(userRepository.findByUsername("dave")).thenReturn(Optional.of(disabled));

        UserDetails result = service.loadUserByUsername("dave");

        assertThat(result.isEnabled()).isFalse();
    }

    // ── usuário não encontrado ────────────────────────────────────────────────

    @Test
    void loadUserByUsername_throwsUsernameNotFound_forMissingUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }
}
