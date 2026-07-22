package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.PermissionNotFoundException;
import com.cernecommerce.core.domain.exception.RoleAlreadyExistsException;
import com.cernecommerce.core.domain.exception.rbac.RoleNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.rbac.Permission;
import com.cernecommerce.core.domain.model.rbac.Role;
import com.cernecommerce.core.ports.out.role.PermissionRepository;
import com.cernecommerce.core.ports.out.role.RoleRepository;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock RoleRepository roleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock UserRepository userRepository;
    @Mock UserCachePort userCachePort;

    RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository, permissionRepository, userRepository, userCachePort);
    }

    @Test
    void createRole_savesAndReturns() {
        Role saved = new Role("ROLE_EDITOR");
        when(roleRepository.findByName("ROLE_EDITOR")).thenReturn(Optional.empty());
        when(roleRepository.save(any())).thenReturn(saved);

        Role result = roleService.createRole("ROLE_EDITOR");

        assertThat(result.getName()).isEqualTo("ROLE_EDITOR");
        verify(roleRepository).save(any());
    }

    @Test
    void createRole_throwsWhenNameAlreadyExists() {
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(new Role("ROLE_ADMIN")));

        assertThatThrownBy(() -> roleService.createRole("ROLE_ADMIN"))
                .isInstanceOf(RoleAlreadyExistsException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void findByName_returnsRole() {
        Role role = new Role("ROLE_USER");
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));

        Role result = roleService.findByName("ROLE_USER");

        assertThat(result.getName()).isEqualTo("ROLE_USER");
    }

    @Test
    void findByName_throwsWhenNotFound() {
        when(roleRepository.findByName("ROLE_GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.findByName("ROLE_GHOST"))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void deleteRole_delegatesWhenExists() {
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new Role("ROLE_USER")));

        roleService.deleteRole("ROLE_USER");

        verify(roleRepository).deleteByName("ROLE_USER");
    }

    @Test
    void deleteRole_throwsWhenNotFound() {
        when(roleRepository.findByName("ROLE_GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.deleteRole("ROLE_GHOST"))
                .isInstanceOf(RoleNotFoundException.class);
        verify(roleRepository, never()).deleteByName(any());
    }

    @Test
    void listAll_delegatesToRepository() {
        PageResult<Role> page = new PageResult<>(List.of(new Role("ROLE_USER")), 0, 10, 1L, 1);
        when(roleRepository.findAll(0, 10)).thenReturn(page);

        PageResult<Role> result = roleService.listAll(0, 10);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    void assignPermission_delegatesWhenBothExist() {
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new Role("ROLE_USER")));
        when(permissionRepository.findByName("USER_READ")).thenReturn(Optional.of(new Permission("USER_READ")));

        roleService.assignPermission("ROLE_USER", "USER_READ");

        verify(roleRepository).addPermissions("ROLE_USER", Set.of("USER_READ"));
    }

    @Test
    void assignPermission_evictsCacheOfEveryUserWithRole() {
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new Role("ROLE_USER")));
        when(permissionRepository.findByName("USER_READ")).thenReturn(Optional.of(new Permission("USER_READ")));
        when(userRepository.findUsernamesByRole("ROLE_USER")).thenReturn(Set.of("alice", "bob"));

        roleService.assignPermission("ROLE_USER", "USER_READ");

        verify(userCachePort).evict("alice");
        verify(userCachePort).evict("bob");
    }

    @Test
    void assignPermission_doesNotEvictWhenRoleOrPermissionMissing() {
        when(roleRepository.findByName("ROLE_GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.assignPermission("ROLE_GHOST", "USER_READ"))
                .isInstanceOf(RoleNotFoundException.class);
        verify(userCachePort, never()).evict(any());
    }

    @Test
    void assignPermission_throwsWhenRoleNotFound() {
        when(roleRepository.findByName("ROLE_GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.assignPermission("ROLE_GHOST", "USER_READ"))
                .isInstanceOf(RoleNotFoundException.class);
        verify(roleRepository, never()).addPermissions(any(), any());
    }

    @Test
    void assignPermission_throwsWhenPermissionNotFound() {
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new Role("ROLE_USER")));
        when(permissionRepository.findByName("GHOST_PERM")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.assignPermission("ROLE_USER", "GHOST_PERM"))
                .isInstanceOf(PermissionNotFoundException.class);
        verify(roleRepository, never()).addPermissions(any(), any());
    }

    @Test
    void removePermission_delegatesWhenRoleExists() {
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new Role("ROLE_USER")));

        roleService.removePermission("ROLE_USER", "USER_READ");

        verify(roleRepository).removePermission("ROLE_USER", "USER_READ");
    }

    @Test
    void removePermission_evictsCacheOfEveryUserWithRole() {
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new Role("ROLE_USER")));
        when(userRepository.findUsernamesByRole("ROLE_USER")).thenReturn(Set.of("alice"));

        roleService.removePermission("ROLE_USER", "USER_READ");

        verify(userCachePort).evict("alice");
    }

    @Test
    void removePermission_throwsWhenRoleNotFound() {
        when(roleRepository.findByName("ROLE_GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.removePermission("ROLE_GHOST", "USER_READ"))
                .isInstanceOf(RoleNotFoundException.class);
        verify(roleRepository, never()).removePermission(any(), any());
        verify(userCachePort, never()).evict(any());
    }
}
