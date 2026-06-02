package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.auth.UpdateProfileResult;
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

    PageResult<User> findFiltered(String search, Boolean enabled, String sortBy, String sortDir, int page, int size);

    /** Remove o usuário e revoga todas as suas sessões. Retorna o username para auditoria. */
    String deleteUser(Long id);

    /**
     * Troca a senha do próprio usuário.
     * Se o usuário tiver 2FA ativo, {@code totpCode} é obrigatório.
     * Se {@code revokeOtherSessions} for true, revoga todos os refresh tokens e bloqueia JWTs anteriores.
     */
    void changeOwnPassword(String username, String currentPassword, String newPassword,
                           String totpCode, boolean revokeOtherSessions);

    /** Ativa ou desativa a conta. Retorna o username para auditoria. */
    String setUserEnabled(Long id, boolean enabled);

    UpdateProfileResult updateUser(Long id, String newUsername, String newEmail);

    /** Auto-atualização: requer senha atual quando o email está sendo alterado. */
    UpdateProfileResult updateOwnProfile(String username, String newUsername, String newEmail, String currentPassword);

    /** Confirma email com código. Retorna o username para auditoria. */
    String verifyEmail(String code);

    void resendVerification(String email);

    /** Inicia o fluxo de recuperação de senha. Sempre silencioso para evitar enumeração de emails. */
    void requestPasswordReset(String email);

    /** Conclui o fluxo de recuperação de senha. Revoga todas as sessões ao final. Retorna o username para auditoria. */
    String resetPassword(String token, String newPassword);

    /** Confirma a troca de email via código enviado ao novo endereço. Retorna o username para auditoria. */
    String confirmEmailChange(String code);
}