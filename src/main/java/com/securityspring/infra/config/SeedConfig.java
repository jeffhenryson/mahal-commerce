package com.security_spring.infra.config;

import com.security_spring.core.domain.model.Permission;
import com.security_spring.core.domain.model.Role;
import com.security_spring.core.ports.in.UserUseCase;
import com.security_spring.core.ports.out.PermissionRepository;
import com.security_spring.core.ports.out.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Set;

@Configuration
@Profile("dev")
public class SeedConfig {

    private static final String[] ADMIN_PERMISSIONS =
            {"USER_CREATE", "USER_READ", "USER_DELETE", "USER_ROLE_ASSIGN", "USER_STATUS"};

    @Bean
    CommandLineRunner seedAll(UserUseCase useCase,
                              RoleRepository roleRepo,
                              PermissionRepository permRepo) {
        return args -> {
            seedRoles(roleRepo);
            seedPermissions(permRepo);
            assignPermissions(roleRepo);
            seedUsers(useCase);
        };
    }

    private void seedRoles(RoleRepository roleRepo) {
        for (String name : new String[]{"ROLE_ADMIN", "ROLE_USER"}) {
            if (roleRepo.findByName(name).isEmpty()) {
                Role r = new Role();
                r.setName(name);
                roleRepo.save(r);
            }
        }
    }

    private void seedPermissions(PermissionRepository permRepo) {
        for (String name : ADMIN_PERMISSIONS) {
            if (permRepo.findByName(name).isEmpty()) {
                Permission p = new Permission();
                p.setName(name);
                permRepo.save(p);
            }
        }
    }

    private void assignPermissions(RoleRepository roleRepo) {
        roleRepo.addPermissions("ROLE_ADMIN", Set.of(ADMIN_PERMISSIONS));
        roleRepo.addPermissions("ROLE_USER", Set.of("USER_READ"));
    }

    private void seedUsers(UserUseCase useCase) {
        if (useCase.findByUsername("admin").isEmpty())
            useCase.createUser("admin", "Admin@dev1", List.of("ROLE_ADMIN"));
        if (useCase.findByUsername("user").isEmpty())
            useCase.createUser("user", "User@dev1", List.of("ROLE_USER"));
    }
}
