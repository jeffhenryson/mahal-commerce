package com.security_spring.infra.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.security_spring.core.ports.in.UserUseCase;

@Configuration
public class SeedConfig {
    @Bean
    CommandLineRunner seedUsers(UserUseCase useCase) {
        return args -> {
            if (useCase.findByUsername("admin").isEmpty()) {
                useCase.createUser("admin", "admin");
                useCase.assignRole("admin", "ROLE_ADMIN");
            }
        };
    }
}