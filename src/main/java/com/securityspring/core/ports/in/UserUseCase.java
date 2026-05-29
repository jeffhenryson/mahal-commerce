package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.auth.User;

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

    void removeRole(String username, String roleName);

    PageResult<User> listAll(int page, int size);

    PageResult<User> findFiltered(String search, Boolean enabled, int page, int size);

    /** Remove o usuário e revoga todas as suas sessões. Retorna o username para auditoria. */
    String deleteUser(Long id);

    void changeOwnPassword(String username, String currentPassword, String newPassword);

    /** Ativa ou desativa a conta. Retorna o username para auditoria. */
    String setUserEnabled(Long id, boolean enabled);

    User updateUser(Long id, String newUsername, String newEmail);

    /** Auto-atualização: requer senha atual quando o email está sendo alterado. */
    User updateOwnProfile(String username, String newUsername, String newEmail, String currentPassword);

    void verifyEmail(String code);

    void resendVerification(String email);

    /** Inicia o fluxo de recuperação de senha. Sempre silencioso para evitar enumeração de emails. */
    void requestPasswordReset(String email);

    /** Conclui o fluxo de recuperação de senha. Revoga todas as sessões ao final. */
    void resetPassword(String token, String newPassword);

    /** Confirma a troca de email via código enviado ao novo endereço. Retorna o username para auditoria. */
    String confirmEmailChange(String code);
}