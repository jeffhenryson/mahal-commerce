package com.securityspring.core.ports.out;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.User;

import java.util.Optional;

public interface UserRepository {
    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    PageResult<User> findAll(int page, int size);

    void deleteById(Long id);

    Optional<User> findByEmail(String email);
}