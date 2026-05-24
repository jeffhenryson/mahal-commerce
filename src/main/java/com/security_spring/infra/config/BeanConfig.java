package com.security_spring.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.security_spring.adapter.in.converter.UserDTOConverter;
import com.security_spring.adapter.out.converter.UserEntityConverter;
import com.security_spring.core.ports.in.UserUseCase;
import com.security_spring.core.ports.out.RoleRepository;
import com.security_spring.core.ports.out.UserRepository;
import com.security_spring.core.service.UserService;

@Configuration
public class BeanConfig {

    @Bean
    public UserEntityConverter userEntityConverter() {
        return new UserEntityConverter();
    }

    @Bean
    public UserUseCase userUseCase(UserRepository userRepository,
            RoleRepository roleRepository) {
        return new UserService(userRepository, roleRepository);
    }

    @Bean
    public UserDTOConverter userDTOConverter() {
        return new UserDTOConverter();
    }
}
