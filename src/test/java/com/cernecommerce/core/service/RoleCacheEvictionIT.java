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

    /**
     * Descoberta: diferente de {@link RoleService#assignPermission} e {@link RoleService#removePermission},
     * que chamam {@code evictUsersWithRole} explicitamente, {@link com.cernecommerce.core.service.PermissionService#deletePermission}
     * apenas remove a {@code Permission} do repositório — não evicta o cache de nenhum usuário
     * das roles afetadas. As duas roles compartilhando a mesma permission e cada uma atribuída
     * a um usuário logado diferente expõem o mesmo gap duas vezes: o cache de ambos os usuários
     * continua com a authority "fantasma" mesmo depois da permission ter sido apagada do banco.
     * Este teste documenta o comportamento REAL observado (sem eviction), não o comportamento
     * desejável — a assinatura da C003 cobre assign/removePermission, não a deleção da Permission.
     */
    @Test
    void deletePermission_doesNotEvictCacheOfUsersAcrossMultipleRoles_knownGap() {
        String suffix = String.valueOf(System.currentTimeMillis());
        String roleNameA = "ROLE_CACHE_TEST_DELPERM_A_" + suffix;
        String roleNameB = "ROLE_CACHE_TEST_DELPERM_B_" + suffix;
        String permissionName = "CACHE_TEST_PERM_DELPERM_" + suffix;
        String usernameA = "cache_test_user_delperm_a_" + suffix;
        String usernameB = "cache_test_user_delperm_b_" + suffix;

        roleUseCase.createRole(roleNameA);
        roleUseCase.createRole(roleNameB);
        permissionUseCase.createPermission(permissionName);
        roleUseCase.assignPermission(roleNameA, permissionName);
        roleUseCase.assignPermission(roleNameB, permissionName);
        userUseCase.createUser(usernameA, "Senha@123", List.of(roleNameA));
        userUseCase.createUser(usernameB, "Senha@123", List.of(roleNameB));

        // Popula o cache dos dois usuários, cada um logado com uma role diferente, mas
        // ambos com a mesma permission — via loadUserByUsername, mesmo mecanismo dos testes acima.
        UserDetails beforeA = userDetailsService.loadUserByUsername(usernameA);
        UserDetails beforeB = userDetailsService.loadUserByUsername(usernameB);
        assertThat(beforeA.getAuthorities()).extracting(Object::toString).contains(permissionName);
        assertThat(beforeB.getAuthorities()).extracting(Object::toString).contains(permissionName);

        permissionUseCase.deletePermission(permissionName);

        UserDetails afterA = userDetailsService.loadUserByUsername(usernameA);
        UserDetails afterB = userDetailsService.loadUserByUsername(usernameB);
        assertThat(afterA.getAuthorities())
                .as("Comportamento real observado: deletePermission NÃO evicta o cache — authority permanece stale para o usuário da role A")
                .extracting(Object::toString)
                .contains(permissionName);
        assertThat(afterB.getAuthorities())
                .as("Mesmo gap se repete para o usuário da role B, que compartilhava a mesma permission já deletada")
                .extracting(Object::toString)
                .contains(permissionName);
    }

    /**
     * Descoberta: {@link RoleService#deleteRole} não chama {@code evictUsersWithRole} — só
     * {@code assignPermission}/{@code removePermission} o fazem. O cache de userDetails do
     * usuário continua com a role e suas permissions mesmo depois da role ter sido apagada do
     * banco (a FK {@code fk_user_roles_role} tem {@code ON DELETE CASCADE}, então a associação
     * é removida no banco sem erro — só o cache que fica desatualizado). Este teste documenta o
     * comportamento REAL observado (sem eviction), não o comportamento desejável.
     */
    @Test
    void deleteRole_doesNotEvictCacheOfAssignedUser_knownGap() {
        String suffix = String.valueOf(System.currentTimeMillis());
        String roleName = "ROLE_CACHE_TEST_DELROLE_" + suffix;
        String permissionName = "CACHE_TEST_PERM_DELROLE_" + suffix;
        String username = "cache_test_user_delrole_" + suffix;

        roleUseCase.createRole(roleName);
        permissionUseCase.createPermission(permissionName);
        roleUseCase.assignPermission(roleName, permissionName);
        userUseCase.createUser(username, "Senha@123", List.of(roleName));

        // Popula o cache já com a role e a permission presentes
        UserDetails before = userDetailsService.loadUserByUsername(username);
        assertThat(before.getAuthorities()).extracting(Object::toString).contains(roleName, permissionName);

        roleUseCase.deleteRole(roleName);

        UserDetails after = userDetailsService.loadUserByUsername(username);
        assertThat(after.getAuthorities())
                .as("Comportamento real observado: deleteRole NÃO evicta o cache — role e permission permanecem stale")
                .extracting(Object::toString)
                .contains(roleName, permissionName);
    }
}
