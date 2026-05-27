package com.securityspring.core.service;

import com.securityspring.core.domain.exception.PermissionAlreadyExistsException;
import com.securityspring.core.domain.exception.PermissionNotFoundException;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.rbac.Permission;
import com.securityspring.core.ports.out.role.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock PermissionRepository permissionRepository;

    PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(permissionRepository);
    }

    @Test
    void createPermission_savesAndReturns() {
        Permission saved = new Permission("USER_READ");
        when(permissionRepository.findByName("USER_READ")).thenReturn(Optional.empty());
        when(permissionRepository.save(any())).thenReturn(saved);

        Permission result = permissionService.createPermission("USER_READ");

        assertThat(result.getName()).isEqualTo("USER_READ");
        verify(permissionRepository).save(any());
    }

    @Test
    void createPermission_throwsWhenNameAlreadyExists() {
        when(permissionRepository.findByName("USER_READ")).thenReturn(Optional.of(new Permission("USER_READ")));

        assertThatThrownBy(() -> permissionService.createPermission("USER_READ"))
                .isInstanceOf(PermissionAlreadyExistsException.class);
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void findByName_returnsPermission() {
        Permission perm = new Permission("USER_WRITE");
        when(permissionRepository.findByName("USER_WRITE")).thenReturn(Optional.of(perm));

        Permission result = permissionService.findByName("USER_WRITE");

        assertThat(result.getName()).isEqualTo("USER_WRITE");
    }

    @Test
    void findByName_throwsWhenNotFound() {
        when(permissionRepository.findByName("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.findByName("GHOST"))
                .isInstanceOf(PermissionNotFoundException.class);
    }

    @Test
    void deletePermission_delegatesWhenExists() {
        when(permissionRepository.findByName("USER_READ")).thenReturn(Optional.of(new Permission("USER_READ")));

        permissionService.deletePermission("USER_READ");

        verify(permissionRepository).deleteByName("USER_READ");
    }

    @Test
    void deletePermission_throwsWhenNotFound() {
        when(permissionRepository.findByName("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.deletePermission("GHOST"))
                .isInstanceOf(PermissionNotFoundException.class);
        verify(permissionRepository, never()).deleteByName(any());
    }

    @Test
    void listAll_delegatesToRepository() {
        PageResult<Permission> page = new PageResult<>(List.of(new Permission("USER_READ")), 0, 10, 1L, 1);
        when(permissionRepository.findAll(0, 10)).thenReturn(page);

        PageResult<Permission> result = permissionService.listAll(0, 10);

        assertThat(result.content()).hasSize(1);
    }
}
