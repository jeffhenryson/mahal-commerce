package com.cernecommerce.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.cernecommerce.core.ports.in.AuditLogsUseCase;
import com.cernecommerce.core.ports.in.AuthUseCase;
import com.cernecommerce.core.ports.in.AvatarUseCase;
import com.cernecommerce.core.ports.in.NotificationPreferenceUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.OAuthLoginUseCase;
import com.cernecommerce.core.ports.in.PermissionUseCase;
import com.cernecommerce.core.ports.in.RoleUseCase;
import com.cernecommerce.core.ports.in.StatsUseCase;
import com.cernecommerce.core.ports.in.SystemConfigUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.token.AccessTokenPort;
import com.cernecommerce.core.ports.out.credential.CredentialVerifierPort;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.EmailVerificationCodeRepository;
import com.cernecommerce.core.ports.out.notification.PasswordResetTokenRepository;
import com.cernecommerce.core.ports.out.ratelimit.LoginAttemptPort;
import com.cernecommerce.core.ports.out.credential.PasswordHashPort;
import com.cernecommerce.core.ports.out.role.PermissionRepository;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import com.cernecommerce.core.ports.out.role.RoleRepository;
import com.cernecommerce.core.ports.out.token.TokenBlocklistPort;
import com.cernecommerce.core.ports.out.twofa.DevChallengeRepository;
import com.cernecommerce.core.ports.out.twofa.TotpBackupCodeRepository;
import com.cernecommerce.core.ports.out.twofa.TotpChallengeTokenRepository;
import com.cernecommerce.core.ports.out.twofa.TotpConfigRepository;
import com.cernecommerce.core.ports.out.twofa.TotpEncryptionPort;
import com.cernecommerce.core.ports.out.twofa.TwoFactorAuthPort;
import com.cernecommerce.core.ports.in.TotpUseCase;
import com.cernecommerce.core.ports.out.oauth.GoogleTokenVerifierPort;
import com.cernecommerce.core.ports.out.user.UserAuthoritiesPort;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import com.cernecommerce.core.ports.out.audit.AuditLogRepository;
import com.cernecommerce.core.ports.out.notification.NotificationPreferenceRepository;
import com.cernecommerce.core.ports.out.notification.NotificationRepository;
import com.cernecommerce.adapter.out.storage.LocalAvatarStorageAdapter;
import com.cernecommerce.core.ports.out.storage.AvatarStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.cernecommerce.core.service.AuditLogsService;
import com.cernecommerce.core.service.AuthService;
import com.cernecommerce.core.service.AvatarService;
import com.cernecommerce.core.service.NotificationPreferenceService;
import com.cernecommerce.core.service.NotificationService;
import com.cernecommerce.core.service.OAuthLoginService;
import com.cernecommerce.core.service.PermissionService;
import com.cernecommerce.core.service.RoleService;
import com.cernecommerce.core.service.StatsService;
import com.cernecommerce.core.service.SystemConfigService;
import com.cernecommerce.core.service.TotpService;
import com.cernecommerce.core.service.UserService;

// Domínios de negócio (esqueletos — sem dependências de port out ainda)
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import com.cernecommerce.core.ports.out.estoque.StockBalanceRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.in.ComprasUseCase;
import com.cernecommerce.core.ports.in.FinanceiroUseCase;
import com.cernecommerce.core.ports.in.EcommerceUseCase;
import com.cernecommerce.core.ports.in.LogisticaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.cernecommerce.core.ports.out.crm.CampaignLogRepository;
import com.cernecommerce.core.ports.out.crm.CustomerNoteRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.crm.StageTransitionRepository;
import com.cernecommerce.core.ports.out.crm.TagRepository;
import com.cernecommerce.core.service.PdvService;
import com.cernecommerce.core.service.EstoqueService;
import com.cernecommerce.core.service.ComprasService;
import com.cernecommerce.core.service.FinanceiroService;
import com.cernecommerce.core.service.EcommerceService;
import com.cernecommerce.core.service.LogisticaService;
import com.cernecommerce.core.service.CrmService;

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
            @Value("${totp.app-name:Cerne Commerce}") String appName) {
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
    RoleUseCase roleUseCase(RoleRepository roleRepository, PermissionRepository permissionRepository,
            UserRepository userRepository, UserCachePort userCachePort) {
        return new RoleService(roleRepository, permissionRepository, userRepository, userCachePort);
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

    // ── Domínios de negócio (esqueletos) ────────────────────────────────────────
    // Wiring sem dependências enquanto os adapters de saída (ports/out) não existem.
    // Ao implementar cada domínio, injetar aqui o(s) repository/port correspondente.

    @Bean
    PdvUseCase pdvUseCase() {
        return new PdvService();
    }

    @Bean
    EstoqueUseCase estoqueUseCase(ProductRepository productRepository, WarehouseRepository warehouseRepository,
            StockBalanceRepository stockBalanceRepository) {
        return new EstoqueService(productRepository, warehouseRepository, stockBalanceRepository);
    }

    @Bean
    ComprasUseCase comprasUseCase() {
        return new ComprasService();
    }

    @Bean
    FinanceiroUseCase financeiroUseCase() {
        return new FinanceiroService();
    }

    @Bean
    EcommerceUseCase ecommerceUseCase() {
        return new EcommerceService();
    }

    @Bean
    LogisticaUseCase logisticaUseCase() {
        return new LogisticaService();
    }

    @Bean
    CrmUseCase crmUseCase(CustomerRepository customerRepository, CustomerNoteRepository customerNoteRepository,
            StageTransitionRepository stageTransitionRepository, TagRepository tagRepository,
            CustomerTagRepository customerTagRepository, CampaignAutomationRepository campaignAutomationRepository,
            CampaignLogRepository campaignLogRepository, EmailPort emailPort) {
        return new CrmService(customerRepository, customerNoteRepository, stageTransitionRepository, tagRepository,
                customerTagRepository, campaignAutomationRepository, campaignLogRepository, emailPort);
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
    NotificationUseCase notificationUseCase(NotificationRepository notificationRepository) {
        return new NotificationService(notificationRepository);
    }

    @Bean
    NotificationPreferenceUseCase notificationPreferenceUseCase(NotificationPreferenceRepository preferenceRepository) {
        return new NotificationPreferenceService(preferenceRepository);
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
