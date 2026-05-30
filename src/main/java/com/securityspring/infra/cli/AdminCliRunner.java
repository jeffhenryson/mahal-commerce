package com.securityspring.infra.cli;

import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.domain.event.AuditEvent.EventType;
import com.securityspring.core.domain.model.auth.SessionInfo;
import com.securityspring.core.domain.model.auth.User;
import com.securityspring.core.ports.in.UserUseCase;
import com.securityspring.core.ports.out.credential.PasswordHashPort;
import com.securityspring.core.ports.out.ratelimit.LoginAttemptPort;
import com.securityspring.core.ports.out.token.RefreshTokenPort;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CLI administrativo ativado exclusivamente com o perfil {@code shell}.
 *
 * <p>Implementado como {@link ApplicationRunner}: executa um único comando passado
 * como argumento posicional e encerra o processo. Não requer dependências extras —
 * usa apenas Spring Boot Core e as portas do domínio já existentes.
 *
 * <p><b>Uso básico (JAR):</b>
 * <pre>
 *   java -jar app.jar --spring.profiles.active=dev,shell &lt;comando&gt; [--opcao=valor]
 * </pre>
 *
 * <p><b>Uso com Maven (dev):</b>
 * <pre>
 *   ./mvnw spring-boot:run \
 *     -Dspring-boot.run.profiles=dev,shell \
 *     "-Dspring-boot.run.arguments=hash-password --password=MinhaS3nh@"
 * </pre>
 *
 * <p><b>Comandos disponíveis:</b>
 * <ul>
 *   <li>{@code hash-password --password=<senha>}</li>
 *   <li>{@code create-admin  --username=<user> --password=<senha>}</li>
 *   <li>{@code enable-user   --id=<id>}</li>
 *   <li>{@code disable-user  --id=<id>}</li>
 *   <li>{@code unlock-account --username=<user>}</li>
 *   <li>{@code list-sessions  --username=<user>}</li>
 *   <li>{@code revoke-sessions --username=<user>}</li>
 *   <li>{@code help}</li>
 * </ul>
 */
@Component
@Profile("shell")
public class AdminCliRunner implements ApplicationRunner {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final PasswordHashPort passwordHashPort;
    private final UserUseCase userUseCase;
    private final RefreshTokenPort refreshTokenPort;
    private final LoginAttemptPort loginAttemptPort;
    private final ApplicationEventPublisher publisher;

    public AdminCliRunner(PasswordHashPort passwordHashPort,
                          UserUseCase userUseCase,
                          RefreshTokenPort refreshTokenPort,
                          LoginAttemptPort loginAttemptPort,
                          ApplicationEventPublisher publisher) {
        this.passwordHashPort = passwordHashPort;
        this.userUseCase = userUseCase;
        this.refreshTokenPort = refreshTokenPort;
        this.loginAttemptPort = loginAttemptPort;
        this.publisher = publisher;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ponto de entrada
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void run(ApplicationArguments args) {
        List<String> positional = args.getNonOptionArgs();
        String command = positional.isEmpty() ? "help" : positional.get(0);

        try {
            String output = switch (command) {
                case "hash-password"    -> hashPassword(args);
                case "create-admin"     -> createAdmin(args);
                case "enable-user"      -> enableUser(args);
                case "disable-user"     -> disableUser(args);
                case "unlock-account"   -> unlockAccount(args);
                case "list-sessions"    -> listSessions(args);
                case "revoke-sessions"  -> revokeSessions(args);
                case "help"             -> help();
                default                 -> unknown(command);
            };
            System.out.println(output);

        } catch (CliException e) {
            System.err.println("[ERRO] " + e.getMessage());
            System.err.println();
            System.err.println(usageFor(command));
            System.exit(1);

        } catch (Exception e) {
            System.err.println("[ERRO] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Comandos
    // ─────────────────────────────────────────────────────────────────────────

    /** hash-password --password=&lt;senha&gt; */
    private String hashPassword(ApplicationArguments args) {
        String password = require(args, "password");
        return passwordHashPort.hash(password);
    }

    /** create-admin --username=&lt;user&gt; --password=&lt;senha&gt; */
    private String createAdmin(ApplicationArguments args) {
        String username = require(args, "username");
        String password = require(args, "password");
        User user = userUseCase.createUser(username, password, List.of("ROLE_ADMIN"));
        return "✓ Administrador criado: " + user.getUsername() + " (id=" + user.getId() + ")";
    }

    /** enable-user --id=&lt;id&gt; */
    private String enableUser(ApplicationArguments args) {
        long id = requireLong(args, "id");
        String username = userUseCase.setUserEnabled(id, true);
        return "✓ Conta ativada: " + username;
    }

    /** disable-user --id=&lt;id&gt; */
    private String disableUser(ApplicationArguments args) {
        long id = requireLong(args, "id");
        String username = userUseCase.setUserEnabled(id, false);
        return "✓ Conta desativada: " + username;
    }

    /** unlock-account --username=&lt;user&gt; */
    private String unlockAccount(ApplicationArguments args) {
        String username = require(args, "username");
        if (!loginAttemptPort.isLocked(username)) {
            return "ℹ Conta '" + username + "' não está bloqueada.";
        }
        loginAttemptPort.recordSuccess(username);
        publisher.publishEvent(AuditEvent.of(EventType.ACCOUNT_LOCKED, username,
                java.util.Map.of("action", "unlocked-by-cli")));
        return "✓ Bloqueio removido: " + username;
    }

    /** list-sessions --username=&lt;user&gt; */
    private String listSessions(ApplicationArguments args) {
        String username = require(args, "username");
        List<SessionInfo> sessions = refreshTokenPort.findActiveSessions(username);

        if (sessions.isEmpty()) {
            return "ℹ Nenhuma sessão ativa para '" + username + "'.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Sessões ativas de '").append(username)
          .append("' (").append(sessions.size()).append("):\n");
        sb.append(String.format("  %-6s  %-19s  %-19s  %-15s  %s%n",
                "ID", "Criada em", "Expira em", "IP", "User-Agent"));
        sb.append("  ").append("─".repeat(88)).append("\n");

        for (SessionInfo s : sessions) {
            sb.append(String.format("  %-6d  %-19s  %-19s  %-15s  %s%n",
                    s.id(),
                    FMT.format(s.createdAt()),
                    FMT.format(s.expiresAt()),
                    s.ipAddress()  != null ? s.ipAddress()          : "-",
                    s.userAgent()  != null ? truncate(s.userAgent(), 40) : "-"));
        }
        return sb.toString().stripTrailing();
    }

    /** revoke-sessions --username=&lt;user&gt; */
    private String revokeSessions(ApplicationArguments args) {
        String username = require(args, "username");
        refreshTokenPort.revokeAll(username);
        return "✓ Todas as sessões revogadas para '" + username + "'.";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Help
    // ─────────────────────────────────────────────────────────────────────────

    private String help() {
        return """
                CLI Administrativo — security-spring
                ─────────────────────────────────────────────────────────────────

                Uso:
                  java -jar app.jar --spring.profiles.active=dev,shell <comando> [--opcao=valor]

                  ./mvnw spring-boot:run \\
                    -Dspring-boot.run.profiles=dev,shell \\
                    "-Dspring-boot.run.arguments=<comando> [--opcao=valor]"

                Comandos disponíveis:
                ─────────────────────────────────────────────────────────────────

                  hash-password    --password=<senha>
                      Gera o hash BCrypt de uma senha em texto claro.

                  create-admin     --username=<user> --password=<senha>
                      Cria um novo usuário com ROLE_ADMIN (ativado imediatamente).

                  enable-user      --id=<id>
                      Ativa a conta de um usuário pelo ID numérico.

                  disable-user     --id=<id>
                      Desativa a conta de um usuário pelo ID numérico.

                  unlock-account   --username=<user>
                      Remove o bloqueio de login por tentativas excessivas.

                  list-sessions    --username=<user>
                      Lista as sessões ativas (refresh tokens não expirados).

                  revoke-sessions  --username=<user>
                      Revoga todas as sessões ativas (logout forçado global).

                  help
                      Exibe esta mensagem.

                Exemplo rápido:
                  java -jar app.jar --spring.profiles.active=dev,shell hash-password --password=MinhaS3nh@1
                """;
    }

    private String unknown(String command) {
        return "Comando desconhecido: '" + command + "'.\nExecute 'help' para ver os comandos disponíveis.";
    }

    private String usageFor(String command) {
        return switch (command) {
            case "hash-password"   -> "Uso: hash-password --password=<senha>";
            case "create-admin"    -> "Uso: create-admin --username=<user> --password=<senha>";
            case "enable-user"     -> "Uso: enable-user --id=<id>";
            case "disable-user"    -> "Uso: disable-user --id=<id>";
            case "unlock-account"  -> "Uso: unlock-account --username=<user>";
            case "list-sessions"   -> "Uso: list-sessions --username=<user>";
            case "revoke-sessions" -> "Uso: revoke-sessions --username=<user>";
            default                -> "Execute 'help' para ver os comandos disponíveis.";
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Retorna o valor de uma opção obrigatória ou lança {@link CliException}. */
    private String require(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            throw new CliException("opção obrigatória ausente: --" + name);
        }
        return values.get(0);
    }

    /** Lê uma opção obrigatória como {@code long}. */
    private long requireLong(ApplicationArguments args, String name) {
        String raw = require(args, name);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new CliException("--" + name + " deve ser um número inteiro (recebido: '" + raw + "')");
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Exceção de uso incorreto do CLI (opção ausente, tipo errado, etc.). */
    static class CliException extends RuntimeException {
        CliException(String message) {
            super(message);
        }
    }
}
