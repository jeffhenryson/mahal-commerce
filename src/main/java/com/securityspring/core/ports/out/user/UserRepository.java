package com.securityspring.core.ports.out.user;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.auth.User;

import java.util.Optional;

public interface UserRepository {
    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    PageResult<User> findAll(int page, int size);

    void deleteById(Long id);

    Optional<User> findByEmail(String email);

    PageResult<User> findFiltered(String search, Boolean enabled, String sortBy, String sortDir, int page, int size);

    long countAll();

    long countEnabled();

    long countDisabled();
}