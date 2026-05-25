package com.security_spring.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.security_spring.adapter.in.converter.UserDTOConverter;
import com.security_spring.adapter.out.converter.UserEntityConverter;
import com.security_spring.core.ports.in.AuthUseCase;
import com.security_spring.core.ports.in.UserUseCase;
import com.security_spring.core.ports.out.AccessTokenPort;
import com.security_spring.core.ports.out.CredentialVerifierPort;
import com.security_spring.core.ports.out.PasswordHashPort;
import com.security_spring.core.ports.out.RefreshTokenPort;
import com.security_spring.core.ports.out.RoleRepository;
import com.security_spring.core.ports.out.TokenBlocklistPort;
import com.security_spring.core.ports.out.UserAuthoritiesPort;
import com.security_spring.core.ports.out.UserRepository;
import com.security_spring.core.service.AuthService;
import com.security_spring.core.service.UserService;

@Configuration
public class BeanConfig {

    @Bean
    public UserEntityConverter userEntityConverter() {
        return new UserEntityConverter();
    }

    @Bean
    public UserUseCase userUseCase(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordHashPort passwordHashPort,
            RefreshTokenPort refreshTokenPort,
            TokenBlocklistPort tokenBlocklistPort) {
        return new UserService(userRepository, roleRepository, passwordHashPort, refreshTokenPort, tokenBlocklistPort);
    }

    @Bean
    public AuthUseCase authUseCase(CredentialVerifierPort credentialVerifier,
            AccessTokenPort accessToken,
            RefreshTokenPort refreshToken,
            UserAuthoritiesPort userAuthorities,
            TokenBlocklistPort tokenBlocklist) {
        return new AuthService(credentialVerifier, accessToken, refreshToken, userAuthorities, tokenBlocklist);
    }

    @Bean
    public UserDTOConverter userDTOConverter() {
        return new UserDTOConverter();
    }
}
