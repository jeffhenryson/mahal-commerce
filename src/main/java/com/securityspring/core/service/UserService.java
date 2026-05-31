package com.securityspring.core.service;

import com.securityspring.core.domain.exception.auth.InvalidPasswordException;
import com.securityspring.core.domain.exception.auth.PasswordResetTokenExpiredException;
import com.securityspring.core.domain.exception.auth.PasswordResetTokenNotFoundException;
import com.securityspring.core.domain.exception.email.EmailAlreadyVerifiedException;
import com.securityspring.core.domain.exception.email.EmailDeliveryException;
import com.securityspring.core.domain.exception.email.EmailVerificationCodeExpiredException;
import com.securityspring.core.domain.exception.email.EmailVerificationCodeNotFoundException;
import com.securityspring.core.domain.exception.rbac.RoleNotFoundException;
import com.securityspring.core.domain.exception.user.EmailAlreadyExistsException;
import com.securityspring.core.domain.exception.user.UserNotFoundException;
import com.securityspring.core.domain.exception.user.UsernameAlreadyExistsException;
import com.securityspring.core.domain.model.auth.EmailVerificationCode;
import com.securityspring.core.domain.model.auth.PasswordResetToken;
import com.securityspring.core.domain.model.auth.UpdateProfileResult;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.rbac.Role;
import com.securityspring.core.domain.model.auth.User;
import com.securityspring.core.ports.out.twofa.TotpBackupCodeRepository;
import com.securityspring.core.ports.out.twofa.TotpChallengeTokenRepository;
import com.securityspring.core.ports.out.twofa.TotpConfigRepository;
import com.securityspring.core.ports.in.UserUseCase;
import com.securityspring.core.ports.out.notification.EmailPort;
import com.securityspring.core.ports.out.notification.EmailVerificationCodeRepository;
import com.securityspring.core.ports.out.notification.PasswordResetTokenRepository;
import com.securityspring.core.ports.out.credential.PasswordHashPort;
import com.securityspring.core.ports.out.token.RefreshTokenPort;
import com.securityspring.core.ports.out.role.RoleRepository;
import com.securityspring.core.ports.out.token.TokenBlocklistPort;
import com.securityspring.core.ports.out.storage.AvatarStoragePort;
import com.securityspring.core.ports.out.user.UserCachePort;
import com.securityspring.core.ports.out.user.UserRepository;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.securityspring.core.domain.PasswordPolicy;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class UserService implements UserUseCase {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHashPort passwordHash;
    private final RefreshTokenPort refreshTokenPort;
    private final TokenBlocklistPort tokenBlocklistPort;
    private final EmailPort emailPort;
    private final EmailVerificationCodeRepository verificationCodeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserCachePort userCachePort;
    private final TotpConfigRepository totpConfigRepository;
    private final TotpBackupCodeRepository totpBackupCodeRepository;
    private final TotpChallengeTokenRepository totpChallengeTokenRepository;
    private final AvatarStoragePort avatarStoragePort;
    private final long verificationCodeTtlMinutes;
    private final long resendCooldownSeconds;
    private final long passwordResetTtlMinutes;
    private final String passwordResetFrontendUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserService(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordHashPort passwordHash,
            RefreshTokenPort refreshTokenPort,
            TokenBlocklistPort tokenBlocklistPort,
            EmailPort emailPort,
            EmailVerificationCodeRepository verificationCodeRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserCachePort userCachePort,
            TotpConfigRepository totpConfigRepository,
            TotpBackupCodeRepository totpBackupCodeRepository,
            TotpChallengeTokenRepository totpChallengeTokenRepository,
            AvatarStoragePort avatarStoragePort,
            long verificationCodeTtlMinutes,
            long resendCooldownSeconds,
            long passwordResetTtlMinutes,
            String passwordResetFrontendUrl) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordHash = passwordHash;
        this.refreshTokenPort = refreshTokenPort;
        this.tokenBlocklistPort = tokenBlocklistPort;
        this.emailPort = emailPort;
        this.verificationCodeRepository = verificationCodeRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userCachePort = userCachePort;
        this.totpConfigRepository = totpConfigRepository;
        this.totpBackupCodeRepository = totpBackupCodeRepository;
        this.totpChallengeTokenRepository = totpChallengeTokenRepository;
        this.avatarStoragePort = avatarStoragePort;
        this.verificationCodeTtlMinutes = verificationCodeTtlMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.passwordResetTtlMinutes = passwordResetTtlMinutes;
        this.passwordResetFrontendUrl = passwordResetFrontendUrl;
    }

    @Override
    @Transactional
    public User createUser(String username, String rawPassword, List<String> roles) {
        return createUser(username, rawPassword, null, roles);
    }

    @Override
    @Transactional
    public User createUser(String username, String rawPassword, String email, List<String> roles) {
        if (!isValidPassword(rawPassword)) throw new InvalidPasswordException();
        String normalizedEmail = normalizeEmail(email);
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new UsernameAlreadyExistsException(username);
        });
        if (normalizedEmail != null) {
            userRepository.findByEmail(normalizedEmail).ifPresent(u -> {
                throw new EmailAlreadyExistsException(normalizedEmail);
            });
        }
        Set<Role> roleSet = resolveRoles(roles);
        User user = User.of(username, passwordHash.hash(rawPassword), roleSet);
        if (normalizedEmail != null) user.assignEmail(normalizedEmail);
        User saved = userRepository.save(user);
        userCachePort.evict(username);
        return saved;
    }

    @Override
    @Transactional(noRollbackFor = EmailDeliveryException.class)
    public User registerUser(String username, String rawPassword, String email, List<String> roles) {
        if (!isValidPassword(rawPassword)) throw new InvalidPasswordException();
        String normalizedEmail = normalizeEmail(email);
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new UsernameAlreadyExistsException(username);
        });
        userRepository.findByEmail(normalizedEmail).ifPresent(u -> {
            throw new EmailAlreadyExistsException(normalizedEmail);
        });
        Set<Role> roleSet = resolveRoles(roles);
        User user = User.ofPendingVerification(username, passwordHash.hash(rawPassword), normalizedEmail, roleSet);
        User saved = userRepository.save(user);
        issueAndSendCode(username, normalizedEmail);
        return saved;
    }

    @Override
    @Transactional
    public String verifyEmail(String code) {
        EmailVerificationCode record = verificationCodeRepository.findByCode(code)
                .orElseThrow(EmailVerificationCodeNotFoundException::new);

        if (record.isExpired()) {
            throw new EmailVerificationCodeExpiredException();
        }

        // CAS atômico: previne que duas requisições concorrentes ativem a mesma conta.
        // Se markAsUsed retornar false, outra requisição já reclamou o código.
        if (!verificationCodeRepository.markAsUsed(code)) {
            throw new EmailVerificationCodeExpiredException();
        }

        User user = userRepository.findByUsername(record.username())
                .orElseThrow(() -> new UserNotFoundException(record.username()));

        if (user.isEmailVerified()) throw new EmailAlreadyVerifiedException();

        user.confirmEmail();
        userRepository.save(user);
        verificationCodeRepository.deleteByUsername(record.username());
        userCachePort.evict(record.username());
        return user.getUsername();
    }

    @Override
    @Transactional(noRollbackFor = EmailDeliveryException.class)
    public void resendVerification(String email) {
        // Return silently when email is not found to prevent user enumeration.
        // Callers always receive 204 regardless of whether the email exists.
        userRepository.findByEmail(normalizeEmail(email)).ifPresent(user -> {
            if (user.isEmailVerified()) return;
            // Cooldown por destinatário: evita spam e custo excessivo no provedor de email.
            boolean onCooldown = verificationCodeRepository.findByUsername(user.getUsername())
                    .map(c -> c.isOnCooldown(resendCooldownSeconds))
                    .orElse(false);
            if (onCooldown) return;
            verificationCodeRepository.deleteByUsername(user.getUsername());
            issueAndSendCode(user.getUsername(), email);
        });
    }

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    @Transactional
    public void assignRole(String username, String roleName) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException(roleName));
        user.addRole(role);
        userRepository.save(user);
        userCachePort.evict(username);
    }

    @Override
    @Transactional
    public void removeRole(String username, String roleName) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException(roleName));
        user.removeRole(role);
        userRepository.save(user);
        userCachePort.evict(username);
    }

    @Override
    public PageResult<User> listAll(int page, int size) {
        return userRepository.findAll(page, size);
    }

    @Override
    public PageResult<User> findFiltered(String search, Boolean enabled, String sortBy, String sortDir, int page, int size) {
        return userRepository.findFiltered(
                (search != null && !search.isBlank()) ? search.trim() : null,
                enabled, sortBy, sortDir, page, size);
    }

    @Override
    @Transactional
    public String deleteUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        String username = user.getUsername();
        refreshTokenPort.revokeAll(username);
        tokenBlocklistPort.blockAllBefore(username, Instant.now());
        verificationCodeRepository.deleteByUsername(username);
        passwordResetTokenRepository.deleteByUsername(username);
        totpChallengeTokenRepository.deleteByUsername(username);
        totpBackupCodeRepository.deleteByUsername(username);
        totpConfigRepository.deleteByUsername(username);
        if (user.getAvatarFilename() != null) avatarStoragePort.delete(user.getAvatarFilename());
        userRepository.deleteById(id);
        userCachePort.evict(username);
        return username;
    }

    @Override
    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        if (!isValidPassword(newPassword)) throw new InvalidPasswordException();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        if (!passwordHash.matches(currentPassword, user.getPassword())) throw new InvalidPasswordException();
        user.changePassword(passwordHash.hash(newPassword));
        userRepository.save(user);
        userCachePort.evict(username);
        refreshTokenPort.revokeAll(username);
        tokenBlocklistPort.blockAllBefore(username, Instant.now());
    }

    @Override
    @Transactional
    public String setUserEnabled(Long id, boolean enabled) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (enabled) user.enable(); else user.disable();
        userRepository.save(user);
        String username = user.getUsername();
        userCachePort.evict(username);
        if (!enabled) {
            refreshTokenPort.revokeAll(username);
            tokenBlocklistPort.blockAllBefore(username, Instant.now());
        }
        return username;
    }

    @Override
    @Transactional(noRollbackFor = EmailDeliveryException.class)
    public UpdateProfileResult updateUser(Long id, String newUsername, String newEmail) {
        String normalizedEmail = normalizeEmail(newEmail);
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        userRepository.findByUsername(newUsername).ifPresent(existing -> {
            if (!existing.getId().equals(id)) throw new UsernameAlreadyExistsException(newUsername);
        });
        String oldUsername = user.getUsername();
        boolean usernameChanged = !oldUsername.equals(newUsername);
        user.rename(newUsername);

        boolean emailChanging = normalizedEmail != null && !normalizedEmail.equalsIgnoreCase(user.getEmail());
        if (emailChanging) {
            userRepository.findByEmail(normalizedEmail).ifPresent(existing -> {
                if (!existing.getId().equals(id)) throw new EmailAlreadyExistsException(normalizedEmail);
            });
            user.setPendingEmail(normalizedEmail);
        }

        User saved = userRepository.save(user);

        // Evict após commit para evitar que leituras dentro da transação (READ_COMMITTED)
        // repovoem o cache com dados ainda não persistidos.
        evictAfterCommit(oldUsername);
        if (usernameChanged) evictAfterCommit(newUsername);

        if (usernameChanged) {
            refreshTokenPort.revokeAll(oldUsername);
            tokenBlocklistPort.blockAllBefore(oldUsername, Instant.now());
        }

        if (emailChanging) {
            verificationCodeRepository.deleteByUsername(saved.getUsername());
            issueAndSendCode(saved.getUsername(), normalizedEmail);
        }

        return new UpdateProfileResult(saved, emailChanging);
    }

    private void evictAfterCommit(String username) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    userCachePort.evict(username);
                }
            });
        } else {
            userCachePort.evict(username);
        }
    }

    @Override
    @Transactional(noRollbackFor = EmailDeliveryException.class)
    public UpdateProfileResult updateOwnProfile(String username, String newUsername, String newEmail, String currentPassword) {
        String normalizedEmail = normalizeEmail(newEmail);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        boolean emailChanging = normalizedEmail != null && !normalizedEmail.equalsIgnoreCase(user.getEmail());
        if (emailChanging) {
            if (currentPassword == null || !passwordHash.matches(currentPassword, user.getPassword())) {
                throw new InvalidPasswordException();
            }
        }
        return updateUser(user.getId(), newUsername, normalizedEmail);
    }

    @Override
    @Transactional(noRollbackFor = EmailDeliveryException.class)
    public void requestPasswordReset(String email) {
        // Silencioso: sem user enumeration — resposta é sempre 204 independente de o email existir.
        String normalizedEmail = normalizeEmail(email);
        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUsername(user.getUsername());
            String token = generateResetToken();
            Instant expiresAt = Instant.now().plus(passwordResetTtlMinutes, ChronoUnit.MINUTES);
            passwordResetTokenRepository.save(user.getUsername(), token, expiresAt);
            String link = passwordResetFrontendUrl + "?token=" + token;
            emailPort.sendPasswordResetLink(normalizedEmail, user.getUsername(), link);
        });
    }

    @Override
    @Transactional
    public String resetPassword(String token, String newPassword) {
        if (!isValidPassword(newPassword)) throw new InvalidPasswordException();

        PasswordResetToken record = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(PasswordResetTokenNotFoundException::new);

        if (record.isExpired() || record.isUsed()) {
            throw new PasswordResetTokenExpiredException();
        }

        if (!passwordResetTokenRepository.markAsUsed(token)) {
            throw new PasswordResetTokenExpiredException();
        }

        User user = userRepository.findByUsername(record.username())
                .orElseThrow(() -> new UserNotFoundException(record.username()));

        user.changePassword(passwordHash.hash(newPassword));
        userRepository.save(user);
        userCachePort.evict(user.getUsername());
        refreshTokenPort.revokeAll(user.getUsername());
        tokenBlocklistPort.blockAllBefore(user.getUsername(), Instant.now());
        return user.getUsername();
    }

    @Override
    @Transactional(noRollbackFor = EmailDeliveryException.class)
    public String confirmEmailChange(String code) {
        EmailVerificationCode record = verificationCodeRepository.findByCode(code)
                .orElseThrow(EmailVerificationCodeNotFoundException::new);

        if (record.isExpired()) throw new EmailVerificationCodeExpiredException();

        if (!verificationCodeRepository.markAsUsed(code)) throw new EmailVerificationCodeExpiredException();

        User user = userRepository.findByUsername(record.username())
                .orElseThrow(() -> new UserNotFoundException(record.username()));

        String oldEmail = user.getEmail();
        String pending = user.getPendingEmail();
        if (pending == null) throw new EmailVerificationCodeNotFoundException();

        user.applyPendingEmail();
        userRepository.save(user);
        verificationCodeRepository.deleteByUsername(record.username());
        userCachePort.evict(record.username());

        emailPort.sendEmailChangeNotification(oldEmail, user.getUsername(), pending);
        return user.getUsername();
    }

    private void issueAndSendCode(String username, String email) {
        String code = generateCode();
        Instant expiresAt = Instant.now().plus(verificationCodeTtlMinutes, ChronoUnit.MINUTES);
        verificationCodeRepository.save(username, code, expiresAt);
        emailPort.sendVerificationCode(email, username, code);
    }

    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private String generateCode() {
        // 12 chars alfanuméricos maiúsculos = 36^12 ≈ 4.7 quatrilhões de combinações (62 bits).
        // Rainbow table para todos os valores ocuparia ~4.7 PB — inviável mesmo com hardware dedicado.
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String generateResetToken() {
        // 32 bytes aleatórios → 43 chars URL-safe base64 → 256 bits de entropia.
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Set<Role> resolveRoles(List<String> roles) {
        Set<Role> roleSet = new HashSet<>();
        if (roles != null) {
            roles.forEach(roleName -> {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new RoleNotFoundException(roleName));
                roleSet.add(role);
            });
        }
        return roleSet;
    }

    private static boolean isValidPassword(String password) {
        return PasswordPolicy.isValid(password);
    }

    private static String normalizeEmail(String email) {
        if (email == null) return null;
        return email.strip().toLowerCase();
    }
}
