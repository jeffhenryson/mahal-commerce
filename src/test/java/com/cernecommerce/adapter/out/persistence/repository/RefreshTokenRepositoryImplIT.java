package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.exception.auth.SessionNotFoundException;
import com.cernecommerce.core.domain.model.auth.SessionInfo;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa {@link RefreshTokenRepositoryImpl#revokeByIdForUser} diretamente contra o banco real
 * (H2, perfil dev) — nenhuma das 17 classes {@code *RepositoryImpl} do projeto tinha teste
 * dedicado antes deste (C009). A proteção contra revogação cruzada de sessão (IDOR) depende
 * inteiramente de {@code RefreshTokenJpaRepository.findActiveByIdAndUsername} filtrar também
 * por username — só cobertura mockada indireta via {@code RefreshTokenServiceTest} não prova
 * que o filtro realmente funciona contra o schema/query reais.
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RefreshTokenRepositoryImplIT {

    @Autowired
    private RefreshTokenPort refreshTokenPort;

    @Autowired
    private UserUseCase userUseCase;

    @Test
    void revokeByIdForUser_throwsAndKeepsSessionActive_whenSessionBelongsToAnotherUser() {
        String suffix = String.valueOf(System.currentTimeMillis());
        String userA = "idor_a_" + suffix;
        String userB = "idor_b_" + suffix;
        userUseCase.createUser(userA, "Secure@123", List.of());
        userUseCase.createUser(userB, "Secure@123", List.of());

        refreshTokenPort.issue(userB);
        Long sessionId = refreshTokenPort.findActiveSessions(userB).get(0).id();

        assertThatThrownBy(() -> refreshTokenPort.revokeByIdForUser(sessionId, userA))
                .isInstanceOf(SessionNotFoundException.class);

        List<SessionInfo> stillActive = refreshTokenPort.findActiveSessions(userB);
        assertThat(stillActive)
                .as("Sessão de B deve continuar ativa após tentativa de revogação cruzada por A")
                .extracting(SessionInfo::id)
                .contains(sessionId);
    }

    @Test
    void revokeByIdForUser_succeeds_whenSessionBelongsToCallingUser() {
        String username = "idor_owner_" + System.currentTimeMillis();
        userUseCase.createUser(username, "Secure@123", List.of());

        refreshTokenPort.issue(username);
        Long sessionId = refreshTokenPort.findActiveSessions(username).get(0).id();

        refreshTokenPort.revokeByIdForUser(sessionId, username);

        assertThat(refreshTokenPort.findActiveSessions(username))
                .as("Sessão revogada pelo próprio dono não deve mais aparecer como ativa")
                .extracting(SessionInfo::id)
                .doesNotContain(sessionId);
    }

    @Test
    void revokeByIdForUser_throws_whenSessionIdDoesNotExist() {
        String username = "idor_ghost_" + System.currentTimeMillis();
        userUseCase.createUser(username, "Secure@123", List.of());

        assertThatThrownBy(() -> refreshTokenPort.revokeByIdForUser(-1L, username))
                .isInstanceOf(SessionNotFoundException.class);
    }
}
