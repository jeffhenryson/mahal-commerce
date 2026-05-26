package com.securityspring.core.service;

import com.securityspring.core.domain.exception.EmailAlreadyExistsException;
import com.securityspring.core.domain.exception.EmailAlreadyVerifiedException;
import com.securityspring.core.domain.exception.EmailVerificationCodeExpiredException;
import com.securityspring.core.domain.exception.EmailVerificationCodeNotFoundException;
import com.securityspring.core.domain.exception.InvalidPasswordException;
import com.securityspring.core.domain.exception.RoleNotFoundException;
import com.securityspring.core.domain.exception.UserNotFoundException;
import com.securityspring.core.domain.exception.UsernameAlreadyExistsException;
import com.securityspring.core.domain.model.EmailVerificationCode;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.Role;
import com.securityspring.core.domain.model.User;
import com.securityspring.core.ports.in.UserUseCase;
import com.securityspring.core.ports.out.EmailPort;
import com.securityspring.core.ports.out.EmailVerificationCodeRepository;
import com.securityspring.core.ports.out.PasswordHashPort;
import com.securityspring.core.ports.out.RefreshTokenPort;
import com.securityspring.core.ports.out.RoleRepository;
import com.securityspring.core.ports.out.TokenBlocklistPort;
import com.securityspring.core.ports.out.UserRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class UserService implements UserUseCase {

    private static final Pattern PASSWORD_COMPLEXITY =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHashPort passwordHash;
    private final RefreshTokenPort refreshTokenPort;
    private final TokenBlocklistPort tokenBlocklistPort;
    private final EmailPort emailPort;
    private final EmailVerificationCodeRepository verificationCodeRepository;
    private final long verificationCodeTtlMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserService(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordHashPort passwordHash,
            RefreshTokenPort refreshTokenPort,
            TokenBlocklistPort tokenBlocklistPort,
            EmailPort emailPort,
            EmailVerificationCodeRepository verificationCodeRepository,
            long verificationCodeTtlMinutes) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordHash = passwordHash;
        this.refreshTokenPort = refreshTokenPort;
        this.tokenBlocklistPort = tokenBlocklistPort;
        this.emailPort = emailPort;
        this.verificationCodeRepository = verificationCodeRepository;
        this.verificationCodeTtlMinutes = verificationCodeTtlMinutes;
    }

    @Override
    public User createUser(String username, String rawPassword, List<String> roles) {
        return createUser(username, rawPassword, null, roles);
    }

    @Override
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
    public void verifyEmail(String code) {
        EmailVerificationCode record = verificationCodeRepository.findByCode(code)
                .orElseThrow(EmailVerificationCodeNotFoundException::new);

        if (record.isExpired() || record.used()) {
            throw new EmailVerificationCodeExpiredException();
        }

        User user = userRepository.findByUsername(record.username())
                .orElseThrow(() -> new UserNotFoundException(record.username()));

        if (user.isEmailVerified()) throw new EmailAlreadyVerifiedException();

        user.confirmEmail();
        userRepository.save(user);
        verificationCodeRepository.deleteByUsername(record.username());
    }

    @Override
    public void resendVerification(String email) {
        // Return silently when email is not found to prevent user enumeration.
        // Callers always receive 204 regardless of whether the email exists.
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.isEmailVerified()) return;
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
    public void assignRole(String username, String roleName) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException(roleName));
        user.addRole(role);
        userRepository.save(user);
    }

    @Override
    public PageResult<User> listAll(int page, int size) {
        return userRepository.findAll(page, size);
    }

    @Override
    public void deleteUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        refreshTokenPort.revokeAll(user.getUsername());
        tokenBlocklistPort.blockAllBefore(user.getUsername(), Instant.now());
        userRepository.deleteById(id);
    }

    @Override
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        if (!isValidPassword(newPassword)) throw new InvalidPasswordException();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        if (!passwordHash.matches(currentPassword, user.getPassword())) throw new InvalidPasswordException();
        user.changePassword(passwordHash.hash(newPassword));
        userRepository.save(user);
        refreshTokenPort.revokeAll(username);
        tokenBlocklistPort.blockAllBefore(username, Instant.now());
    }

    @Override
    public void setUserEnabled(Long id, boolean enabled) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (enabled) user.enable(); else user.disable();
        userRepository.save(user);
        if (!enabled) {
            refreshTokenPort.revokeAll(user.getUsername());
            tokenBlocklistPort.blockAllBefore(user.getUsername(), Instant.now());
        }
    }

    @Override
    public User updateUser(Long id, String newUsername) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        userRepository.findByUsername(newUsername).ifPresent(existing -> {
            if (!existing.getId().equals(id)) throw new UsernameAlreadyExistsException(newUsername);
        });
        user.rename(newUsername);
        return userRepository.save(user);
    }

    private void issueAndSendCode(String username, String email) {
        String code = generateCode();
        Instant expiresAt = Instant.now().plus(verificationCodeTtlMinutes, ChronoUnit.MINUTES);
        verificationCodeRepository.save(username, code, expiresAt);
        emailPort.sendVerificationCode(email, username, code);
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
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
        return password != null && password.length() >= 8 && PASSWORD_COMPLEXITY.matcher(password).matches();
    }
}
