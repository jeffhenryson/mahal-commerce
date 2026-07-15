package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.StatsResult;
import com.cernecommerce.core.ports.in.StatsUseCase;
import com.cernecommerce.core.ports.out.role.PermissionRepository;
import com.cernecommerce.core.ports.out.role.RoleRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional(readOnly = true)
    public StatsResult getStats() {
        return new StatsResult(
                userRepository.countAll(),
                userRepository.countEnabled(),
                userRepository.countDisabled(),
                roleRepository.countAll(),
                permissionRepository.countAll()
        );
    }
}
