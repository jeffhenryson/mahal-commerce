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
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.rbac.Role;
import com.securityspring.core.domain.model.auth.User;
import com.securityspring.core.ports.in.UserUseCase;
import com.securityspring.core.ports.out.notification.EmailPort;
import com.securityspring.core.ports.out.notification.EmailVerificationCodeRepository;
import com.securityspring.core.ports.out.notification.PasswordResetTokenRepository;
import com.securityspring.core.ports.out.credential.PasswordHashPort;
import com.securityspring.core.ports.out.token.RefreshTokenPort;
import com.securityspring.core.ports.out.role.RoleRepository;
import com.securityspring.core.ports.out.token.TokenBlocklistPort;
import com.securityspring.core.ports.out.user.UserCachePort;
import com.securityspring.core.ports.out.user.UserRepository;

import org.springframework.transaction.annotation.Transactional;

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
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new UsernameAlreadyExistsException(username);
        });
        if (email != null) {
            userRepository.findByEmail(email).ifPresent(u -> {
                throw new EmailAlreadyExistsException(email);
            });
        }
        Set<Role> roleSet = resolveRoles(roles);
        User user = User.of(username, passwordHash.hash(rawPassword), roleSet);
        if (email != null) user.assignEmail(email);
        return userRepository.save(user);
    }

    @Override
    @Transactional(noRollbackFor = EmailDeliveryException.class)
    public User registerUser(String username, String rawPassword, String email, List<String> roles) {
        if (!isValidPassword(rawPassword)) throw new InvalidPasswordException();
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new UsernameAlreadyExistsException(username);
        });
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new EmailAlreadyExistsException(email);
        });
        Set<Role> roleSet = resolveRoles(roles);
        User user = User.ofPendingVerification(username, passwordHash.hash(rawPassword), email, roleSet);
        User saved = userRepository.save(user);
        issueAndSendCode(username, email);
        return saved;
    }

    @Override
    @Transactional
    public void verifyEmail(String code) {
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
    }

    @Override
    @Transactional(noRollbackFor = EmailDeliveryException.class)
    public void resendVerification(String email) {
        // Return silently when email is not found to prevent user enumeration.
        // Callers always receive 204 regardless of whether the email exists.
        userRepository.findByEmail(email).ifPresent(user -> {
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
    public PageResult<User> listAll(int page, int size) {
        return userRepository.findAll(page, size);
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
    public User updateUser(Long id, String newUsername, String newEmail) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        userRepository.findByUsername(newUsername).ifPresent(existing -> {
            if (!existing.getId().equals(id)) throw new UsernameAlreadyExistsException(newUsername);
        });
        String oldUsername = user.getUsername();
        boolean usernameChanged = !oldUsername.equals(newUsername);
        user.rename(newUsername);

        boolean emailChanged = newEmail != null && !newEmail.equalsIgnoreCase(user.getEmail());
        if (emailChanged) {
            userRepository.findByEmail(newEmail).ifPresent(existing -> {
                if (!existing.getId().equals(id)) throw new EmailAlreadyExistsException(newEmail);
            });
            user.changeEmail(newEmail);
            // Disable the account until the new email address is verified.
            user.disable();
        }

        User saved = userRepository.save(user);
        userCachePort.evict(oldUsername);
        if (usernameChanged) userCachePort.evict(newUsername);

        if (usernameChanged) {
            // JWT claims carry the old username; revoke all active sessions to force re-login.
            refreshTokenPort.revokeAll(oldUsername);
            tokenBlocklistPort.blockAllBefore(oldUsername, Instant.now());
        }

        if (emailChanged) {
            // Revoke active sessions — account is disabled until new email is verified.
            refreshTokenPort.revokeAll(saved.getUsername());
            tokenBlocklistPort.blockAllBefore(saved.getUsername(), Instant.now());
            verificationCodeRepository.deleteByUsername(saved.getUsername());
            issueAndSendCode(saved.getUsername(), newEmail);
        }

        return saved;
    }

    @Override
    @Transactional(noRollbackFor = EmailDeliveryException.class)
    public User updateOwnProfile(String username, String newUsername, String newEmail, String currentPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        boolean emailChanging = newEmail != null && !newEmail.equalsIgnoreCase(user.getEmail());
        if (emailChanging) {
            if (currentPassword == null || !passwordHash.matches(currentPassword, user.getPassword())) {
                throw new InvalidPasswordException();
            }
        }

        // Username change: reuse existing logic (passes current email to avoid re-triggering admin flow)
        User result = updateUser(user.getId(), newUsername, emailChanging ? user.getEmail() : newEmail);

        // Email change: pending confirmation flow — account stays active, old email is notified on confirm
        if (emailChanging) {
            userRepository.findByEmail(newEmail).ifPresent(existing -> {
                if (!existing.getId().equals(result.getId())) throw new EmailAlreadyExistsException(newEmail);
            });
            result.setPendingEmail(newEmail);
            User saved = userRepository.save(result);
            userCachePort.evict(saved.getUsername());
            verificationCodeRepository.deleteByUsername(saved.getUsername());
            issueAndSendCode(saved.getUsername(), newEmail);
            return saved;
        }

        return result;
    }

    @Override
    @Transactional(noRollbackFor = EmailDeliveryException.class)
    public void requestPasswordReset(String email) {
        // Silencioso: sem user enumeration — resposta é sempre 204 independente de o email existir.
        userRepository.findByEmail(email).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUsername(user.getUsername());
            String token = generateResetToken();
            Instant expiresAt = Instant.now().plus(passwordResetTtlMinutes, ChronoUnit.MINUTES);
            passwordResetTokenRepository.save(user.getUsername(), token, expiresAt);
            String link = passwordResetFrontendUrl + "?token=" + token;
            emailPort.sendPasswordResetLink(email, user.getUsername(), link);
        });
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
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
}
