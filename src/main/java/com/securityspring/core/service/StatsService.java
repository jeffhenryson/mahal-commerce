package com.securityspring.core.service;

import com.securityspring.adapter.in.dtos.response.StatsResponseDTO;
import com.securityspring.core.ports.in.StatsUseCase;
import com.securityspring.core.ports.out.role.PermissionRepository;
import com.securityspring.core.ports.out.role.RoleRepository;
import com.securityspring.core.ports.out.user.UserRepository;

public class StatsService implements StatsUseCase {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public StatsService(UserRepository userRepository,
                        RoleRepository roleRepository,
                        PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public StatsResponseDTO getStats() {
        return new StatsResponseDTO(
                userRepository.countAll(),
                userRepository.countEnabled(),
                roleRepository.countAll(),
                permissionRepository.countAll()
        );
    }
}
