package com.securityspring.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.securityspring.core.ports.in.AuditLogsUseCase;
import com.securityspring.core.ports.in.AuthUseCase;
import com.securityspring.core.ports.in.AvatarUseCase;
import com.securityspring.core.ports.in.OAuthLoginUseCase;
import com.securityspring.core.ports.in.PermissionUseCase;
import com.securityspring.core.ports.in.RoleUseCase;
import com.securityspring.core.ports.in.StatsUseCase;
import com.securityspring.core.ports.in.SystemConfigUseCase;
import com.securityspring.core.ports.in.UserUseCase;
import com.securityspring.core.ports.out.SystemConfigPort;
import com.securityspring.core.ports.out.token.AccessTokenPort;
import com.securityspring.core.ports.out.credential.CredentialVerifierPort;
import com.securityspring.core.ports.out.notification.EmailPort;
import com.securityspring.core.ports.out.notification.EmailVerificationCodeRepository;
import com.securityspring.core.ports.out.notification.PasswordResetTokenRepository;
import com.securityspring.core.ports.out.ratelimit.LoginAttemptPort;
import com.securityspring.core.ports.out.credential.PasswordHashPort;
import com.securityspring.core.ports.out.role.PermissionRepository;
import com.securityspring.core.ports.out.token.RefreshTokenPort;
import com.securityspring.core.ports.out.role.RoleRepository;
import com.securityspring.core.ports.out.token.TokenBlocklistPort;
import com.securityspring.core.ports.out.twofa.DevChallengeRepository;
import com.securityspring.core.ports.out.twofa.TotpBackupCodeRepository;
import com.securityspring.core.ports.out.twofa.TotpChallengeTokenRepository;
import com.securityspring.core.ports.out.twofa.TotpConfigRepository;
import com.securityspring.core.ports.out.twofa.TotpEncryptionPort;
import com.securityspring.core.ports.out.twofa.TwoFactorAuthPort;
import com.securityspring.core.ports.in.TotpUseCase;
import com.securityspring.core.ports.out.oauth.GoogleTokenVerifierPort;
import com.securityspring.core.ports.out.user.UserAuthoritiesPort;
import com.securityspring.core.ports.out.user.UserCachePort;
import com.securityspring.core.ports.out.user.UserRepository;
import com.securityspring.core.ports.out.audit.AuditLogRepository;
import com.securityspring.adapter.out.storage.LocalAvatarStorageAdapter;
import com.securityspring.core.ports.out.storage.AvatarStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.securityspring.core.service.AuditLogsService;
import com.securityspring.core.service.AuthService;
import com.securityspring.core.service.AvatarService;
import com.securityspring.core.service.OAuthLoginService;
import com.securityspring.core.service.PermissionService;
import com.securityspring.core.service.RoleService;
import com.securityspring.core.service.StatsService;
import com.securityspring.core.service.SystemConfigService;
import com.securityspring.core.service.TotpService;
import com.securityspring.core.service.UserService;

import java.nio.file.Path;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;

@Configuration
@EnableScheduling
@EnableAsync
@EnableCaching
class CoreBeanConfig {

    @Bean
    UserUseCase userUseCase(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordHashPort passwordHashPort,
            RefreshTokenPort refreshTokenPort,
            TokenBlocklistPort tokenBlocklistPort,
            EmailPort emailPort,
            EmailVerificationCodeRepository verificationCodeRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserCachePort userCachePort,
            TotpConfigRepository totpConfigRepository,
            TotpBackupCodeRepository totpBackupCodeRepository,
            TotpChallengeTokenRepository totpChallengeTokenRepository,
            TwoFactorAuthPort twoFactorAuthPort,
            AvatarStoragePort avatarStoragePort,
            @Value("${email.verification.ttl-minutes:15}") long verificationCodeTtlMinutes,
            @Value("${email.verification.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${password-reset.ttl-minutes:15}") long passwordResetTtlMinutes,
            @Value("${password-reset.frontend-url:http://localhost:3000/reset-password}") String passwordResetFrontendUrl) {
        return new UserService(userRepository, roleRepository, passwordHashPort,
                refreshTokenPort, tokenBlocklistPort, emailPort,
                verificationCodeRepository, passwordResetTokenRepository, userCachePort,
                totpConfigRepository, totpBackupCodeRepository, totpChallengeTokenRepository,
                twoFactorAuthPort, avatarStoragePort, verificationCodeTtlMinutes, resendCooldownSeconds,
                passwordResetTtlMinutes, passwordResetFrontendUrl);
    }

    @Bean
    TotpService totpService(TotpConfigRepository totpConfigRepository,
            TotpBackupCodeRepository totpBackupCodeRepository,
            TotpChallengeTokenRepository totpChallengeTokenRepository,
            DevChallengeRepository devChallengeRepository,
            TotpEncryptionPort totpEncryptionPort,
            PasswordHashPort passwordHashPort,
            UserRepository userRepository,
            @Value("${totp.app-name:security-spring}") String appName) {
        SecretGenerator secretGenerator = new DefaultSecretGenerator();
        CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        return new TotpService(totpConfigRepository, totpBackupCodeRepository, totpChallengeTokenRepository,
                devChallengeRepository, totpEncryptionPort, passwordHashPort, userRepository,
                secretGenerator, codeVerifier, appName);
    }

    @Bean
    TotpUseCase totpUseCase(TotpService totpService) {
        return totpService;
    }

    @Bean
    AuthUseCase authUseCase(CredentialVerifierPort credentialVerifier,
            AccessTokenPort accessToken,
            RefreshTokenPort refreshToken,
            UserAuthoritiesPort userAuthorities,
            TokenBlocklistPort tokenBlocklist,
            LoginAttemptPort loginAttempt,
            TotpService totpService,
            SystemConfigPort systemConfigPort) {
        return new AuthService(credentialVerifier, accessToken, refreshToken,
                userAuthorities, tokenBlocklist, loginAttempt, totpService, systemConfigPort);
    }

    @Bean
    RoleUseCase roleUseCase(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        return new RoleService(roleRepository, permissionRepository);
    }

    @Bean
    PermissionUseCase permissionUseCase(PermissionRepository permissionRepository) {
        return new PermissionService(permissionRepository);
    }

    @Bean
    StatsUseCase statsUseCase(UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository) {
        return new StatsService(userRepository, roleRepository, permissionRepository);
    }

    @Bean
    OAuthLoginUseCase oAuthLoginUseCase(GoogleTokenVerifierPort tokenVerifier,
            UserRepository userRepository,
            RoleRepository roleRepository,
            AccessTokenPort accessToken,
            RefreshTokenPort refreshToken,
            UserAuthoritiesPort userAuthorities,
            UserCachePort userCachePort) {
        return new OAuthLoginService(tokenVerifier, userRepository, roleRepository,
                accessToken, refreshToken, userAuthorities, userCachePort);
    }

    @Bean
    AuditLogsUseCase auditLogsUseCase(AuditLogRepository auditLogRepository) {
        return new AuditLogsService(auditLogRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "avatar.storage.type", havingValue = "local", matchIfMissing = true)
    AvatarStoragePort avatarStoragePort(AvatarProperties avatarProps) {
        return new LocalAvatarStorageAdapter(Path.of(avatarProps.getStorageDir()));
    }

    @Bean
    SystemConfigUseCase systemConfigUseCase(SystemConfigPort configPort) {
        return new SystemConfigService(configPort);
    }

    @Bean
    AvatarUseCase avatarUseCase(UserRepository userRepository,
            AvatarStoragePort storagePort,
            UserCachePort userCachePort,
            AvatarProperties avatarProps) {
        return new AvatarService(userRepository, storagePort, userCachePort,
                avatarProps.getMaxSizeBytes(), avatarProps.getBaseUrl());
    }
}
