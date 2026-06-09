package com.securityspring.infra.notification;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry();

    @Test
    void register_adiciona_emitter_para_usuario() {
        SseEmitter emitter = new SseEmitter(1000L);
        registry.register("alice", emitter);

        assertThat(registry.activeConnections("alice")).isEqualTo(1);
    }

    @Test
    void register_aceita_multiplos_emitters_por_usuario() {
        registry.register("alice", new SseEmitter(1000L));
        registry.register("alice", new SseEmitter(1000L));

        assertThat(registry.activeConnections("alice")).isEqualTo(2);
    }

    @Test
    void send_para_usuario_sem_emitters_nao_lanca_excecao() {
        assertThat(registry.activeConnections("nobody")).isZero();
        registry.send("nobody", "payload");
    }

    @Test
    void remove_diminui_contagem_de_emitters() {
        SseEmitter emitter = new SseEmitter(1000L);
        registry.register("alice", emitter);
        assertThat(registry.activeConnections("alice")).isEqualTo(1);

        registry.remove("alice", emitter);

        assertThat(registry.activeConnections("alice")).isZero();
    }

    @Test
    void remove_usuario_inexistente_nao_lanca_excecao() {
        registry.remove("nobody", new SseEmitter(1000L));
    }

    @Test
    void activeConnections_retorna_zero_para_usuario_sem_emitters() {
        assertThat(registry.activeConnections("nobody")).isZero();
    }
}
