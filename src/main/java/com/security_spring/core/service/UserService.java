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
import com.security_spring.core.ports.out.RoleRepository;
import com.security_spring.core.ports.out.UserRepository;

import java.util.List;
import java.util.Optional;

public class UserService implements UserUseCase {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHashPort passwordHash;

    public UserService(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordHashPort passwordHash) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordHash = passwordHash;
    }

    @Override
    public User createUser(String username, String rawPassword, List<String> roles) {
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new UsernameAlreadyExistsException(username);
        });
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordHash.hash(rawPassword));
        if (roles != null) {
            roles.forEach(roleName -> {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new RoleNotFoundException(roleName));
                user.addRole(role);
            });
        }
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
        userRepository.deleteById(id);
    }

    @Override
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (!passwordHash.matches(currentPassword, user.getPassword())) {
            throw new InvalidPasswordException();
        }

        user.setPassword(passwordHash.hash(newPassword));
        userRepository.save(user);
    }
}
