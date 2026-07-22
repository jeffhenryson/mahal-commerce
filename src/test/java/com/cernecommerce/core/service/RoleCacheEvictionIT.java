package com.cernecommerce.core.service;

import com.cernecommerce.core.ports.in.PermissionUseCase;
import com.cernecommerce.core.ports.in.RoleUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.infra.security.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa que {@link RoleService#assignPermission} e {@link RoleService#removePermission} evictam
 * o cache de {@code userDetails} de todo usuário com a role alterada (C003) — sem isso, o
 * usuário mantém as authorities antigas até o TTL do cache expirar, mesmo em resposta a um
 * incidente de segurança que exija revogação imediata.
 *
 * <p>Sobe o contexto Spring real (perfil dev, H2) para exercitar o {@link org.springframework.cache.CacheManager}
 * de fato — um teste com repositórios mockados não provaria que a eviction realmente acontece
 * no cache usado em runtime pelo {@link CustomUserDetailsService}.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoleCacheEvictionIT {

    @Autowired
    private RoleUseCase roleUseCase;

    @Autowired
    private PermissionUseCase permissionUseCase;

    @Autowired
    private UserUseCase userUseCase;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Test
    void assignPermission_evictsCacheSoNextLoadReflectsNewAuthority() {
        String suffix = String.valueOf(System.currentTimeMillis());
        String roleName = "ROLE_CACHE_TEST_" + suffix;
        String permissionName = "CACHE_TEST_PERM_" + suffix;
        String username = "cache_test_user_" + suffix;

        roleUseCase.createRole(roleName);
        permissionUseCase.createPermission(permissionName);
        userUseCase.createUser(username, "Senha@123", List.of(roleName));

        // Popula o cache com as authorities de antes da alteração
        UserDetails before = userDetailsService.loadUserByUsername(username);
        assertThat(before.getAuthorities()).extracting(Object::toString).doesNotContain(permissionName);

        roleUseCase.assignPermission(roleName, permissionName);

        UserDetails after = userDetailsService.loadUserByUsername(username);
        assertThat(after.getAuthorities())
                .as("Cache deve ter sido evictado — a nova permissão deve aparecer sem esperar o TTL")
                .extracting(Object::toString)
                .contains(permissionName);
    }

    @Test
    void removePermission_evictsCacheSoNextLoadDropsRemovedAuthority() {
        String suffix = String.valueOf(System.currentTimeMillis());
        String roleName = "ROLE_CACHE_TEST_RM_" + suffix;
        String permissionName = "CACHE_TEST_PERM_RM_" + suffix;
        String username = "cache_test_user_rm_" + suffix;

        roleUseCase.createRole(roleName);
        permissionUseCase.createPermission(permissionName);
        roleUseCase.assignPermission(roleName, permissionName);
        userUseCase.createUser(username, "Senha@123", List.of(roleName));

        // Popula o cache já com a permissão presente
        UserDetails before = userDetailsService.loadUserByUsername(username);
        assertThat(before.getAuthorities()).extracting(Object::toString).contains(permissionName);

        roleUseCase.removePermission(roleName, permissionName);

        UserDetails after = userDetailsService.loadUserByUsername(username);
        assertThat(after.getAuthorities())
                .as("Cache deve ter sido evictado — a permissão removida não deve mais aparecer")
                .extracting(Object::toString)
                .doesNotContain(permissionName);
    }
}
