package com.securityspring.infra.config;

import com.securityspring.core.ports.in.PermissionUseCase;
import com.securityspring.core.ports.in.RoleUseCase;
import com.securityspring.core.ports.in.UserUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@Profile("dev")
public class SeedConfig {

    private static final Logger log = LoggerFactory.getLogger(SeedConfig.class);

    private static final String[] ADMIN_PERMISSIONS = {
        "USER_CREATE", "USER_READ", "USER_UPDATE", "USER_DELETE", "USER_ROLE_ASSIGN", "USER_STATUS",
        "ROLE_READ", "ROLE_CREATE", "ROLE_DELETE", "ROLE_MANAGE_PERMISSIONS",
        "PERMISSION_READ", "PERMISSION_CREATE", "PERMISSION_DELETE",
        "AUDIT_READ"
    };

    @Bean
    CommandLineRunner seedAll(UserUseCase userUseCase,
                              RoleUseCase roleUseCase,
                              PermissionUseCase permissionUseCase,
                              @Value("${seed.admin.password:Admin@dev1}") String adminPassword,
                              @Value("${seed.user.password:User@dev1}") String userPassword) {
        return args -> {
            seedPermissions(permissionUseCase);
            seedRoles(roleUseCase);
            seedUsers(userUseCase, adminPassword, userPassword);
        };
    }

    private void seedPermissions(PermissionUseCase permissionUseCase) {
        for (String name : ADMIN_PERMISSIONS) {
            try {
                permissionUseCase.createPermission(name);
            } catch (Exception e) {
                log.debug("seed.permission.skip name={} reason={}", name, e.getMessage());
            }
        }
    }

    private void seedRoles(RoleUseCase roleUseCase) {
        for (String name : new String[]{"ROLE_ADMIN", "ROLE_USER"}) {
            try {
                roleUseCase.createRole(name);
            } catch (Exception e) {
                log.debug("seed.role.skip name={} reason={}", name, e.getMessage());
            }
        }
        for (String perm : ADMIN_PERMISSIONS) {
            try {
                roleUseCase.assignPermission("ROLE_ADMIN", perm);
            } catch (Exception e) {
                log.debug("seed.role.assignPermission.skip role=ROLE_ADMIN perm={} reason={}", perm, e.getMessage());
            }
        }
        try {
            roleUseCase.assignPermission("ROLE_USER", "USER_READ");
        } catch (Exception e) {
            log.debug("seed.role.assignPermission.skip role=ROLE_USER perm=USER_READ reason={}", e.getMessage());
        }
    }

    private void seedUsers(UserUseCase userUseCase, String adminPassword, String userPassword) {
        if (userUseCase.findByUsername("admin").isEmpty())
            userUseCase.createUser("admin", adminPassword, List.of("ROLE_ADMIN"));
        if (userUseCase.findByUsername("user").isEmpty())
            userUseCase.createUser("user", userPassword, List.of("ROLE_USER"));
    }
}
