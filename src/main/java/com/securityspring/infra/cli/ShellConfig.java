package com.securityspring.infra.cli;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.shell.core.command.annotation.EnableCommand;

/**
 * Configuração que registra os comandos do shell administrativo.
 *
 * <p>Ativado somente com o perfil {@code shell} — evita que o Spring Shell
 * interfira na inicialização normal da aplicação (web/hml/prod).</p>
 *
 * <p>{@code @EnableCommand} escaneia {@link AdminShellCommands} em busca de
 * métodos anotados com {@code @Command} e registra um {@code CommandFactoryBean}
 * para cada um, tornando-os disponíveis no terminal interativo.</p>
 */
@Configuration
@Profile("shell")
@EnableCommand(AdminShellCommands.class)
public class ShellConfig {
}
