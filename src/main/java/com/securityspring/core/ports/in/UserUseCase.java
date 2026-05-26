package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserUseCase {
    /** Criação administrativa sem verificação de email (ex: SeedConfig, admin API). */
    User createUser(String username, String rawPassword, List<String> roles);

    /** Criação administrativa com email opcional — salvo sem trigger de verificação. */
    User createUser(String username, String rawPassword, String email, List<String> roles);

    /** Registro externo: cria conta desabilitada e envia código de verificação por email. */
    User registerUser(String username, String rawPassword, String email, List<String> roles);

    User getUserById(Long id);

    Optional<User> findByUsername(String username);

    void assignRole(String username, String roleName);

    PageResult<User> listAll(int page, int size);

    void deleteUser(Long id);

    void changeOwnPassword(String username, String currentPassword, String newPassword);

    void setUserEnabled(Long id, boolean enabled);

    User updateUser(Long id, String newUsername, String newEmail);

    void verifyEmail(String code);

    void resendVerification(String email);
}