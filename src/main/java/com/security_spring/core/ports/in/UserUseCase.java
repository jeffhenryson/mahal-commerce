package com.security_spring.core.ports.in;

import com.security_spring.core.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserUseCase {
    User createUser(String username, String rawPassword);

    User getUserById(Long id);

    Optional<User> findByUsername(String username);

    void assignRole(String username, String roleName);

    List<User> listAll();

    void deleteUser(Long id);
}