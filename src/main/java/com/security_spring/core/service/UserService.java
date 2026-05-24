package com.security_spring.core.service;

import com.security_spring.core.domain.exception.UserNotFoundException;
import com.security_spring.core.domain.exception.UsernameAlreadyExistsException;
import com.security_spring.core.domain.model.Role;
import com.security_spring.core.domain.model.User;
import com.security_spring.core.ports.in.UserUseCase;
import com.security_spring.core.ports.out.RoleRepository;
import com.security_spring.core.ports.out.UserRepository;

import java.util.List;
import java.util.Optional;

public class UserService implements UserUseCase {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository,
            RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public User createUser(String username, String rawPassword) {
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new UsernameAlreadyExistsException(username);
        });
        User user = new User();
        user.setUsername(username);
        user.setPassword(rawPassword); // encode em adapter/out (ou via uma port de PasswordEncoder)
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
                .orElseGet(() -> roleRepository.save(new Role(roleName)));

        user.addRole(role);
        userRepository.save(user);
    }

    @Override
    public List<User> listAll() {
        return userRepository.findAll();
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
