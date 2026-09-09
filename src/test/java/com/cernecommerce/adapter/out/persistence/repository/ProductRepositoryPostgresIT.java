package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Testa {@code ProductRepositoryImpl.findAll} — a listagem do catálogo — contra um Postgres real
 * via Testcontainers. Habilitar com: {@code ENABLE_TC=true ./mvnw test}
 *
 * <p><b>EST-C024.</b> Existe pelo mesmo motivo do {@link PedidoRepositoryPostgresIT}, e para o
 * mesmo bug: bind de parâmetro nulo sem tipo inferível, que o Postgres recebe como {@code bytea}.
 * A EST-C020 envolveu {@code :search} em {@code unaccent(...)}, e com isso o parâmetro perdeu a
 * única âncora de tipo que tinha ({@code LIKE :search}); a outra ocorrência é
 * {@code :search IS NULL}, igualmente sem tipo. O resultado foi
 * {@code ERROR: function unaccent(bytea) does not exist} na preparação do statement — ou seja,
 * {@code GET /estoque/products} respondendo 500 para <b>qualquer</b> chamada, com ou sem busca, e
 * a tela de catálogo do admin inteira em branco.</p>
 *
 * <p>O {@code EstoqueRepositoryIT} não pega: roda em H2, onde {@code unaccent} é o alias de
 * {@code H2Unaccent} — que recebe {@code Object} e aceita o bind sem reclamar. Só o dialeto real
 * prova que o {@code CAST(:search AS String)} da correção resolve, e que a extensão da V122
 * responde pela função.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
class ProductRepositoryPostgresIT {

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
        // O perfil `dev` traz spring.sql.init.mode=always apontando para db/dev/dev-schema.sql,
        // que desde a EST-C020 contém `CREATE ALIAS ... FOR "..H2Unaccent.unaccent"` — sintaxe de
        // H2, que o Postgres recusa e que derrubaria o contexto antes do primeiro teste. Aqui o
        // schema vem do Flyway, ligado logo acima; o arquivo de dev não tem nada a acrescentar.
        r.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired ProductRepositoryImpl productRepository;

    private static ProductFilter semFiltro() {
        return new ProductFilter(null, null, null, null, null, null, null);
    }

    @Test
    void findAll_withoutFilters_doesNotThrowOnRealPostgres() {
        // Reproduz literalmente a chamada da tela de catálogo: GET /estoque/products sem busca.
        assertThatCode(() -> productRepository.findAll(0, 20, semFiltro(),
                ProductSortField.ID, SortDirection.DESC))
                .doesNotThrowAnyException();
    }

    @Test
    void findAll_withSearchFilled_doesNotThrowOnRealPostgres() {
        assertThatCode(() -> productRepository.findAll(0, 20,
                new ProductFilter("carvao", null, null, null, null, null, null),
                ProductSortField.NAME, SortDirection.ASC))
                .doesNotThrowAnyException();
    }

    /**
     * O que a EST-C020 prometia entregar, e que o bug de tipagem impediu de rodar uma única vez:
     * a grafia digitada e a gravada podem divergir no acento, e a busca precisa cruzar as duas
     * direções.
     */
    @Test
    void findAll_searchIgnoresAccentInBothDirections() {
        productRepository.save(Product.create("IT-ACC-001", "Carvão", "carvao", List.of()));
        productRepository.save(Product.create("IT-ACC-002", "Essencia", "essencia", List.of()));

        PageResult<Product> semAcento = productRepository.findAll(0, 20,
                new ProductFilter("carvao", null, null, null, null, null, null),
                ProductSortField.ID, SortDirection.ASC);
        PageResult<Product> comAcento = productRepository.findAll(0, 20,
                new ProductFilter("Essência", null, null, null, null, null, null),
                ProductSortField.ID, SortDirection.ASC);

        // Termo sem acento acha o nome gravado com acento...
        assertThat(semAcento.content()).extracting(Product::sku).contains("IT-ACC-001");
        // ...e o termo com acento acha o nome gravado sem.
        assertThat(comAcento.content()).extracting(Product::sku).contains("IT-ACC-002");
    }
}
