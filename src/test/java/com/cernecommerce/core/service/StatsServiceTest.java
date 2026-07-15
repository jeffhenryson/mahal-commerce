package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.StatsResult;
import com.cernecommerce.core.ports.out.role.PermissionRepository;
import com.cernecommerce.core.ports.out.role.RoleRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PermissionRepository permissionRepository;

    StatsService statsService;

    @BeforeEach
    void setUp() {
        statsService = new StatsService(userRepository, roleRepository, permissionRepository);
    }

    @Test
    void getStats_returns_aggregated_counts() {
        when(userRepository.countAll()).thenReturn(100L);
        when(userRepository.countEnabled()).thenReturn(85L);
        when(userRepository.countDisabled()).thenReturn(15L);
        when(roleRepository.countAll()).thenReturn(5L);
        when(permissionRepository.countAll()).thenReturn(20L);

        StatsResult result = statsService.getStats();

        assertThat(result.totalUsers()).isEqualTo(100L);
        assertThat(result.activeUsers()).isEqualTo(85L);
        assertThat(result.disabledUsers()).isEqualTo(15L);
        assertThat(result.totalRoles()).isEqualTo(5L);
        assertThat(result.totalPermissions()).isEqualTo(20L);
    }

    @Test
    void getStats_delegates_to_all_three_repositories() {
        when(userRepository.countAll()).thenReturn(0L);
        when(userRepository.countEnabled()).thenReturn(0L);
        when(userRepository.countDisabled()).thenReturn(0L);
        when(roleRepository.countAll()).thenReturn(0L);
        when(permissionRepository.countAll()).thenReturn(0L);

        statsService.getStats();

        verify(userRepository).countAll();
        verify(userRepository).countEnabled();
        verify(userRepository).countDisabled();
        verify(roleRepository).countAll();
        verify(permissionRepository).countAll();
    }

    @Test
    void getStats_returns_zeros_when_empty() {
        when(userRepository.countAll()).thenReturn(0L);
        when(userRepository.countEnabled()).thenReturn(0L);
        when(userRepository.countDisabled()).thenReturn(0L);
        when(roleRepository.countAll()).thenReturn(0L);
        when(permissionRepository.countAll()).thenReturn(0L);

        StatsResult result = statsService.getStats();

        assertThat(result.totalUsers()).isZero();
        assertThat(result.activeUsers()).isZero();
        assertThat(result.disabledUsers()).isZero();
        assertThat(result.totalRoles()).isZero();
        assertThat(result.totalPermissions()).isZero();
    }
}
