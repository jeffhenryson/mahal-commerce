package com.securityspring.infra.config.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

@Configuration
@Profile("dev")
public class DevModeWarningConfig {

    private static final Logger log = LoggerFactory.getLogger(DevModeWarningConfig.class);

    @EventListener(ApplicationStartedEvent.class)
    public void warnDevMode() {
        log.warn("╔══════════════════════════════════════════════════════════════╗");
        log.warn("║                  ⚠  PERFIL DEV ATIVO  ⚠                     ║");
        log.warn("║  Banco: H2 em memória (dados perdidos ao reiniciar)          ║");
        log.warn("║  CORS: * (todas as origens permitidas)                       ║");
        log.warn("║  Swagger: http://localhost:8080/swagger-ui/index.html        ║");
        log.warn("║  Usuário seed: admin / Admin@dev1  (senha pública!)          ║");
        log.warn("║  NUNCA use este perfil em produção.                          ║");
        log.warn("║  Em produção defina: SPRING_PROFILES_ACTIVE=prod             ║");
        log.warn("╚══════════════════════════════════════════════════════════════╝");
    }
}
