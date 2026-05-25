package com.securityspring.core.service;

import com.securityspring.core.domain.exception.InvalidPasswordException;
import com.securityspring.core.domain.exception.RoleNotFoundException;
import com.securityspring.core.domain.exception.UserNotFoundException;
import com.securityspring.core.domain.exception.UsernameAlreadyExistsException;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.Role;
import com.securityspring.core.domain.model.User;
import com.securityspring.core.ports.in.UserUseCase;
import com.securityspring.core.ports.out.PasswordHashPort;
import com.securityspring.core.ports.out.RefreshTokenPort;
import com.securityspring.core.ports.out.RoleRepository;
import com.securityspring.core.ports.out.TokenBlocklistPort;
import com.securityspring.core.ports.out.UserRepository;

import java.time.Instant;
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

    public UserService(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordHashPort passwordHash,
            RefreshTokenPort refreshTokenPort,
            TokenBlocklistPort tokenBlocklistPort) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordHash = passwordHash;
        this.refreshTokenPort = refreshTokenPort;
        this.tokenBlocklistPort = tokenBlocklistPort;
    }

    @Override
    public User createUser(String username, String rawPassword, List<String> roles) {
        if (!isValidPassword(rawPassword)) {
            throw new InvalidPasswordException();
        }
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new UsernameAlreadyExistsException(username);
        });
        Set<Role> roleSet = new HashSet<>();
        if (roles != null) {
            roles.forEach(roleName -> {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new RoleNotFoundException(roleName));
                roleSet.add(role);
            });
        }
        User user = User.of(username, passwordHash.hash(rawPassword), roleSet);
        return userRepository.save(user);
    }

    @Override
    public User getUserById(Long id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public void assignRole(String username, String roleName) {
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        Role role = roleRepository
                .findByName(roleName)
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
        userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        userRepository.deleteById(id);
    }

    @Override
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        if (!isValidPassword(newPassword)) {
            throw new InvalidPasswordException();
        }
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (!passwordHash.matches(currentPassword, user.getPassword())) {
            throw new InvalidPasswordException();
        }

        user.changePassword(passwordHash.hash(newPassword));
        userRepository.save(user);

        // Invalidate all active sessions so stolen credentials cannot be reused.
        refreshTokenPort.revokeAll(username);
        tokenBlocklistPort.blockAllBefore(username, Instant.now());
    }

    @Override
    public void setUserEnabled(Long id, boolean enabled) {
        User user = userRepository
                .findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        if (enabled) user.enable(); else user.disable();
        userRepository.save(user);

        if (!enabled) {
            refreshTokenPort.revokeAll(user.getUsername());
            tokenBlocklistPort.blockAllBefore(user.getUsername(), Instant.now());
        }
    }

    private static boolean isValidPassword(String password) {
        return password != null && password.length() >= 8 && PASSWORD_COMPLEXITY.matcher(password).matches();
    }

    @Override
    public User updateUser(Long id, String newUsername) {
        User user = userRepository
                .findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        userRepository.findByUsername(newUsername).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new UsernameAlreadyExistsException(newUsername);
            }
        });
        user.rename(newUsername);
        return userRepository.save(user);
    }
}
