package com.security_spring.infra.config;

import com.security_spring.adapter.out.entities.PermissionEntity;
import com.security_spring.adapter.out.repository.PermissionJpaRepository;
import com.security_spring.adapter.out.repository.RoleJpaRepository;
import com.security_spring.core.ports.in.UserUseCase;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Configuration
@Profile("dev")
public class SeedConfig {

    private static final String[] ADMIN_PERMISSIONS =
            {"USER_CREATE", "USER_READ", "USER_UPDATE", "USER_DELETE", "USER_ROLE_ASSIGN"};

    @Bean
    CommandLineRunner seedAll(UserUseCase useCase,
                               RoleJpaRepository roleRepo,
                               PermissionJpaRepository permRepo,
                               PlatformTransactionManager txManager) {
        return args -> {
            if (useCase.findByUsername("admin").isEmpty())
                useCase.createUser("admin", "Admin@dev1", List.of("ROLE_ADMIN"));
            if (useCase.findByUsername("user").isEmpty())
                useCase.createUser("user", "User@dev1", List.of("ROLE_USER"));

            new TransactionTemplate(txManager).execute(status -> {
                for (String name : ADMIN_PERMISSIONS) {
                    permRepo.findByName(name).orElseGet(() -> {
                        PermissionEntity p = new PermissionEntity();
                        p.setName(name);
                        return permRepo.save(p);
                    });
                }
                roleRepo.findByName("ROLE_ADMIN").ifPresent(role -> {
                    for (String name : ADMIN_PERMISSIONS)
                        permRepo.findByName(name).ifPresent(p -> role.getPermissions().add(p));
                    roleRepo.save(role);
                });
                roleRepo.findByName("ROLE_USER").ifPresent(role -> {
                    permRepo.findByName("USER_READ").ifPresent(p -> role.getPermissions().add(p));
                    roleRepo.save(role);
                });
                return null;
            });
        };
    }
}