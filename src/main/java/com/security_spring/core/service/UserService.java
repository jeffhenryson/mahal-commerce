package com.security_spring.core.service;

import com.security_spring.core.domain.exception.InvalidPasswordException;
import com.security_spring.core.domain.exception.RoleNotFoundException;
import com.security_spring.core.domain.exception.UserNotFoundException;
import com.security_spring.core.domain.exception.UsernameAlreadyExistsException;
import com.security_spring.core.domain.model.PageResult;
import com.security_spring.core.domain.model.Role;
import com.security_spring.core.domain.model.User;
import com.security_spring.core.ports.in.UserUseCase;
import com.security_spring.core.ports.out.PasswordHashPort;
import com.security_spring.core.ports.out.RefreshTokenPort;
import com.security_spring.core.ports.out.RoleRepository;
import com.security_spring.core.ports.out.TokenBlocklistPort;
import com.security_spring.core.ports.out.UserRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public class UserService implements UserUseCase {

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
    @Transactional
    public User createUser(String username, String rawPassword, List<String> roles) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new InvalidPasswordException();
        }
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new UsernameAlreadyExistsException(username);
        });
        java.util.Set<Role> roleSet = new java.util.HashSet<>();
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
    @Transactional
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
    @Transactional
    public void deleteUser(Long id) {
        userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        userRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (!passwordHash.matches(currentPassword, user.getPassword())) {
            throw new InvalidPasswordException();
        }

        user.setPassword(passwordHash.hash(newPassword));
        userRepository.save(user);

        // Invalidate all active sessions so stolen credentials cannot be reused.
        refreshTokenPort.revokeAll(username);
        tokenBlocklistPort.blockAllBefore(username, java.time.Instant.now());
    }

    @Override
    @Transactional
    public void setUserEnabled(Long id, boolean enabled) {
        User user = userRepository
                .findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        user.setEnabled(enabled);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public User updateUser(Long id, String newUsername) {
        User user = userRepository
                .findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        userRepository.findByUsername(newUsername).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new UsernameAlreadyExistsException(newUsername);
            }
        });
        user.setUsername(newUsername);
        return userRepository.save(user);
    }
}
