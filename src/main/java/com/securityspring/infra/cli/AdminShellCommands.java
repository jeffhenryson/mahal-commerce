package com.securityspring.infra.cli;

import com.securityspring.core.domain.model.SessionInfo;
import com.securityspring.core.domain.model.User;
import com.securityspring.core.ports.in.UserUseCase;
import com.securityspring.core.ports.out.credential.PasswordHashPort;
import com.securityspring.core.ports.out.ratelimit.LoginAttemptPort;
import com.securityspring.core.ports.out.token.RefreshTokenPort;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Comandos administrativos via Spring Shell (perfil {@code shell}).
 *
 * <p>Ative o shell combinando o perfil base com o perfil {@code shell}:
 * <pre>
 *   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,shell
 *   # ou com JAR:
 *   java -jar app.jar --spring.profiles.active=dev,shell
 * </pre>
 * O perfil {@code shell} desativa o servidor web — apenas os comandos
 * abaixo ficam acessíveis no terminal interativo.</p>
 *
 * <p>Comandos disponíveis:
 * <ul>
 *   <li>{@code hash-password --password <senha>} — gera hash BCrypt</li>
 *   <li>{@code create-admin --username X --password Y} — cria admin</li>
 *   <li>{@code enable-user --id N} — ativa conta</li>
 *   <li>{@code disable-user --id N} — desativa conta</li>
 *   <li>{@code unlock-account --username X} — remove bloqueio de login</li>
 *   <li>{@code list-sessions --username X} — lista sessões ativas</li>
 *   <li>{@code revoke-sessions --username X} — revoga todas as sessões</li>
 * </ul></p>
 */
@Component
public class AdminShellCommands {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final PasswordHashPort passwordHashPort;
    private final UserUseCase userUseCase;
    private final RefreshTokenPort refreshTokenPort;
    private final LoginAttemptPort loginAttemptPort;

    public AdminShellCommands(PasswordHashPort passwordHashPort,
                              UserUseCase userUseCase,
                              RefreshTokenPort refreshTokenPort,
                              LoginAttemptPort loginAttemptPort) {
        this.passwordHashPort = passwordHashPort;
        this.userUseCase = userUseCase;
        this.refreshTokenPort = refreshTokenPort;
        this.loginAttemptPort = loginAttemptPort;
    }

    // ── Utilitários ───────────────────────────────────────────────────────────

    @Command(name = {"hash-password"}, description = "Gera o hash BCrypt de uma senha em texto claro")
    public String hashPassword(
            @Option(longName = "password", description = "Senha em texto claro", required = true) String password) {

        return passwordHashPort.hash(password);
    }

    // ── Gestão de usuários ────────────────────────────────────────────────────

    @Command(name = {"create-admin"}, description = "Cria um novo usuário com ROLE_ADMIN")
    public String createAdmin(
            @Option(longName = "username", description = "Username do novo administrador", required = true) String username,
            @Option(longName = "password", description = "Senha em texto claro", required = true) String password) {

        User created = userUseCase.createUser(username, password, List.of("ROLE_ADMIN"));
        return "✓ Administrador criado: " + created.getUsername() + " (id=" + created.getId() + ")";
    }

    @Command(name = {"enable-user"}, description = "Ativa a conta de um usuário por ID")
    public String enableUser(
            @Option(longName = "id", description = "ID numérico do usuário", required = true) Long id) {

        String username = userUseCase.setUserEnabled(id, true);
        return "✓ Conta ativada: " + username;
    }

    @Command(name = {"disable-user"}, description = "Desativa a conta de um usuário por ID")
    public String disableUser(
            @Option(longName = "id", description = "ID numérico do usuário", required = true) Long id) {

        String username = userUseCase.setUserEnabled(id, false);
        return "✓ Conta desativada: " + username;
    }

    @Command(name = {"unlock-account"}, description = "Remove o bloqueio de login de um usuário")
    public String unlockAccount(
            @Option(longName = "username", description = "Username do usuário bloqueado", required = true) String username) {

        if (!loginAttemptPort.isLocked(username)) {
            return "ℹ Conta '" + username + "' não está bloqueada.";
        }
        loginAttemptPort.recordSuccess(username);
        return "✓ Bloqueio removido: " + username;
    }

    // ── Gestão de sessões ─────────────────────────────────────────────────────

    @Command(name = {"list-sessions"}, description = "Lista as sessões ativas de um usuário")
    public String listSessions(
            @Option(longName = "username", description = "Username do usuário", required = true) String username) {

        List<SessionInfo> sessions = refreshTokenPort.findActiveSessions(username);
        if (sessions.isEmpty()) {
            return "ℹ Nenhuma sessão ativa para '" + username + "'.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Sessões ativas de '").append(username).append("' (").append(sessions.size()).append("):\n");
        sb.append(String.format("  %-6s  %-20s  %-20s  %-16s  %s%n",
                "ID", "Criada em", "Expira em", "IP", "User-Agent"));
        sb.append("  ").append("-".repeat(90)).append("\n");

        for (SessionInfo s : sessions) {
            sb.append(String.format("  %-6d  %-20s  %-20s  %-16s  %s%n",
                    s.id(),
                    FMT.format(s.createdAt()),
                    FMT.format(s.expiresAt()),
                    s.ipAddress() != null ? s.ipAddress() : "-",
                    s.userAgent()  != null ? truncate(s.userAgent(), 40) : "-"));
        }
        return sb.toString().stripTrailing();
    }

    @Command(name = {"revoke-sessions"}, description = "Revoga todas as sessões de um usuário (logout forçado)")
    public String revokeSessions(
            @Option(longName = "username", description = "Username do usuário", required = true) String username) {

        refreshTokenPort.revokeAll(username);
        return "✓ Todas as sessões revogadas para '" + username + "'.";
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
