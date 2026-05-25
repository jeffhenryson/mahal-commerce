package com.securityspring.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.securityspring.adapter.in.converter.UserDTOConverter;
import com.securityspring.adapter.out.converter.UserEntityConverter;
import com.securityspring.core.ports.in.AuthUseCase;
import com.securityspring.core.ports.in.PermissionUseCase;
import com.securityspring.core.ports.in.RoleUseCase;
import com.securityspring.core.ports.in.UserUseCase;
import com.securityspring.core.ports.out.AccessTokenPort;
import com.securityspring.core.ports.out.CredentialVerifierPort;
import com.securityspring.core.ports.out.PasswordHashPort;
import com.securityspring.core.ports.out.PermissionRepository;
import com.securityspring.core.ports.out.RefreshTokenPort;
import com.securityspring.core.ports.out.RoleRepository;
import com.securityspring.core.ports.out.TokenBlocklistPort;
import com.securityspring.core.ports.out.UserAuthoritiesPort;
import com.securityspring.core.ports.out.UserRepository;
import com.securityspring.core.service.AuthService;
import com.securityspring.core.service.PermissionService;
import com.securityspring.core.service.RoleService;
import com.securityspring.core.service.UserService;

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
    public RoleUseCase roleUseCase(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        return new RoleService(roleRepository, permissionRepository);
    }

    @Bean
    public PermissionUseCase permissionUseCase(PermissionRepository permissionRepository) {
        return new PermissionService(permissionRepository);
    }

    @Bean
    public UserDTOConverter userDTOConverter() {
        return new UserDTOConverter();
    }
}
