package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Testa {@code OrderRepositoryImpl.findAll} e {@code OrderReportRepositoryImpl.summarize} contra
 * um Postgres real via Testcontainers. Habilitar com: {@code ENABLE_TC=true ./mvnw test}
 *
 * <p>{@code PedidoRepositoryIT} roda em H2 com {@code MODE=PostgreSQL} — um modo de
 * compatibilidade sintática que não reproduz dois bugs reais que só apareceram contra Postgres de
 * verdade: (1) o filtro de data original de {@code GET /orders} usava
 * {@code (:from IS NULL OR o.createdAt >= :from)} sem tipo explícito, e o Postgres recusava
 * inferir o tipo do bind quando {@code from}/{@code to} vinham nulos; (2) o CAST explícito que
 * corrigiria isso ({@code CAST(:from AS timestamp)}) tem um bug conhecido de interação
 * Hibernate/pgjdbc que troca o tipo do parâmetro por {@code bytea}
 * ({@code cannot cast type bytea to timestamp without time zone}) — quebrou de novo, com um erro
 * diferente, mesmo com {@code from}/{@code to} sempre preenchidos nas queries de agregação do
 * P1. A correção definitiva trocou o filtro opcional de {@code findAll} por
 * {@link org.springframework.data.jpa.domain.Specification} (que nunca emite um bind nulo
 * ambíguo) e removeu o CAST das queries de agregação, onde nunca foi necessário. Só um Postgres
 * real prova que nenhuma das duas formas quebra de novo.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
class PedidoRepositoryPostgresIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        r.add("management.health.redis.enabled", () -> "false");
    }

    @Autowired OrderRepositoryImpl orderRepository;
    @Autowired OrderReportRepositoryImpl orderReportRepository;

    @Test
    void findAll_withoutFilters_doesNotThrowOnRealPostgres() {
        // Reproduz literalmente GET /orders sem query params — o 500 confirmado em produção.
        assertThatCode(() -> orderRepository.findAll(null, null, null, null, null, 0, 20))
                .doesNotThrowAnyException();
    }

    @Test
    void findAll_withOnlyFromFilled_doesNotThrowOnRealPostgres() {
        assertThatCode(() -> orderRepository
                .findAll(null, null, null, java.time.Instant.now().minusSeconds(60), null, 0, 20))
                .doesNotThrowAnyException();
    }

    @Test
    void findAll_withOnlyToFilled_doesNotThrowOnRealPostgres() {
        assertThatCode(() -> orderRepository
                .findAll(null, null, null, null, java.time.Instant.now().plusSeconds(60), 0, 20))
                .doesNotThrowAnyException();
    }

    /**
     * O {@code LEFT JOIN ProductEntity p ON p.sku = oi.sku} (join por valor, sem FK) e o
     * {@code CAST(o.createdAt AS date)} de {@code findDailyRevenue} são sintaxe nova neste
     * código-base — vale provar contra o dialeto real, não só H2 (ver
     * {@link OrderReportRepositoryIT}).
     */
    @Test
    void summarize_doesNotThrowOnRealPostgres() {
        assertThatCode(() -> orderReportRepository.summarize(null, null, null,
                java.time.Instant.now().minusSeconds(3600), java.time.Instant.now().plusSeconds(3600), 10))
                .doesNotThrowAnyException();
    }
}
