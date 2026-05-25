package com.securityspring.infra.config;

import com.securityspring.core.ports.in.PermissionUseCase;
import com.securityspring.core.ports.in.RoleUseCase;
import com.securityspring.core.ports.in.UserUseCase;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@Profile("dev")
public class SeedConfig {

    private static final String[] ADMIN_PERMISSIONS = {
        "USER_CREATE", "USER_READ", "USER_UPDATE", "USER_DELETE", "USER_ROLE_ASSIGN", "USER_STATUS",
        "ROLE_READ", "ROLE_CREATE", "ROLE_DELETE", "ROLE_MANAGE_PERMISSIONS",
        "PERMISSION_READ", "PERMISSION_CREATE", "PERMISSION_DELETE"
    };

    @Bean
    CommandLineRunner seedAll(UserUseCase userUseCase,
                              RoleUseCase roleUseCase,
                              PermissionUseCase permissionUseCase) {
        return args -> {
            seedPermissions(permissionUseCase);
            seedRoles(roleUseCase);
            seedUsers(userUseCase);
        };
    }

    private void seedPermissions(PermissionUseCase permissionUseCase) {
        for (String name : ADMIN_PERMISSIONS) {
            try { permissionUseCase.createPermission(name); } catch (Exception ignored) {}
        }
    }

    private void seedRoles(RoleUseCase roleUseCase) {
        for (String name : new String[]{"ROLE_ADMIN", "ROLE_USER"}) {
            try { roleUseCase.createRole(name); } catch (Exception ignored) {}
        }
        for (String perm : ADMIN_PERMISSIONS) {
            try { roleUseCase.assignPermission("ROLE_ADMIN", perm); } catch (Exception ignored) {}
        }
        try { roleUseCase.assignPermission("ROLE_USER", "USER_READ"); } catch (Exception ignored) {}
    }

    private void seedUsers(UserUseCase userUseCase) {
        if (userUseCase.findByUsername("admin").isEmpty())
            userUseCase.createUser("admin", "Admin@dev1", List.of("ROLE_ADMIN"));
        if (userUseCase.findByUsername("user").isEmpty())
            userUseCase.createUser("user", "User@dev1", List.of("ROLE_USER"));
    }
}
