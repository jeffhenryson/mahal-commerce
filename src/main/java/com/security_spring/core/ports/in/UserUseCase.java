package com.security_spring.core.ports.in;

import com.security_spring.core.domain.model.PageResult;
import com.security_spring.core.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserUseCase {
    User createUser(String username, String rawPassword, List<String> roles);

    User getUserById(Long id);

    Optional<User> findByUsername(String username);

    void assignRole(String username, String roleName);

    PageResult<User> listAll(int page, int size);

    void deleteUser(Long id);

    void changeOwnPassword(String username, String currentPassword, String newPassword);

    void setUserEnabled(Long id, boolean enabled);
}