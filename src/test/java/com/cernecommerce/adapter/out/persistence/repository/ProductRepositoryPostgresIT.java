package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.service.EstoqueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
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
    @Autowired EstoqueService estoqueService;
    @Autowired JdbcTemplate jdbc;

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

    /**
     * EST-F029: digitar o nome da categoria traz a categoria inteira, mesmo quando nenhum produto
     * tem a palavra no nome — sem caixa e sem acento, como o resto da busca.
     */
    @Test
    void findAll_searchMatchesCategoryName() {
        productRepository.save(Product.create("IT-CAT-001", "Seda King Size", "Alfafa", List.of()));
        productRepository.save(Product.create("IT-CAT-002", "Piteira Longa", "Alfafa", List.of()));
        productRepository.save(Product.create("IT-CAT-003", "Isqueiro", "Acessórios", List.of()));

        PageResult<Product> minusculo = productRepository.findAll(0, 20,
                new ProductFilter("alfafa", null, null, null, null, null, null),
                ProductSortField.ID, SortDirection.ASC);
        PageResult<Product> comAcento = productRepository.findAll(0, 20,
                new ProductFilter("ALFÁFA", null, null, null, null, null, null),
                ProductSortField.ID, SortDirection.ASC);

        assertThat(minusculo.content()).extracting(Product::sku)
                .contains("IT-CAT-001", "IT-CAT-002").doesNotContain("IT-CAT-003");
        assertThat(comAcento.content()).extracting(Product::sku).contains("IT-CAT-001", "IT-CAT-002");
    }

    /** EST-F029 no catálogo público: a mesma regra, e sem o 500 de bind do EST-C024. */
    @Test
    void findAllActiveAndPriced_searchMatchesCategoryName() {
        productRepository.save(Product.create("IT-SHOP-001", "Tesoura Inox", "Alfafa Shop", List.of(),
                Pricing.of(null, null, new BigDecimal("15.00"))));

        assertThatCode(() -> productRepository.findAllActiveAndPriced(0, 20, null, null, null))
                .doesNotThrowAnyException();
        assertThat(productRepository.findAllActiveAndPriced(0, 20, null, null, "alfafa shop").content())
                .extracting(Product::sku).contains("IT-SHOP-001");
    }

    /**
     * Reproduz o relato do front-end (mahal-admin): PATCH marcando sessionProduct/sessionsPerUnit/
     * openRoshPrice, seguido de uma releitura simulando o GET seguinte. Passa por dentro do
     * {@code @Transactional updateProduct} de verdade (não mockado) contra Postgres real — o
     * {@code EstoqueServiceTest} já cobre a mesma semântica com repositório mockado, e passa; aqui
     * o alvo é o merge do Hibernate entre a entidade carregada por {@code findBySku} e a nova
     * instância que {@code ProductRepositoryImpl.save} constrói, que só se manifesta contra o
     * dialeto real (mesma lição do EST-C024 acima).
     */
    @Test
    void updateProduct_tableSession_persisteContraPostgresReal() {
        productRepository.save(Product.create("SESS-IT-001", "Sessão de teste", "sessao", List.of()));

        estoqueService.updateProduct("SESS-IT-001", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(null, true, 10, new BigDecimal("60.00")));

        Product reloaded = productRepository.findBySku("SESS-IT-001").orElseThrow();

        assertThat(reloaded.sessionProduct()).isTrue();
        assertThat(reloaded.sessionsPerUnit()).isEqualTo(10);
        assertThat(reloaded.openRoshPrice()).isEqualByComparingTo("60.00");
    }

    /**
     * EST-F030 — rede contra coluna esquecida: toda coluna do schema com "sku" no nome tem que
     * estar em {@link ProductRepositoryImpl#SKU_COLUMNS}. Sem FK para cascatear, uma migration
     * nova com SKU que não entre na lista deixaria linha órfã a cada troca — e esse teste é o
     * único lugar onde isso aparece antes de produção.
     */
    @Test
    void renameSku_coversEverySkuColumnInSchema() {
        List<String> schemaColumns = jdbc.queryForList("""
                SELECT c.table_name || '.' || c.column_name
                FROM information_schema.columns c
                JOIN information_schema.tables t
                  ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                WHERE c.table_schema = 'public' AND t.table_type = 'BASE TABLE'
                  AND c.column_name LIKE '%sku%'
                """, String.class);

        assertThat(ProductRepositoryImpl.SKU_COLUMNS).containsExactlyInAnyOrderElementsOf(schemaColumns);
    }

    /** EST-F030 ponta a ponta: pai, variação, saldo, movimento e receita de kit seguem o SKU novo. */
    @Test
    void changeSku_propagatesToStockAndKitRecipe() {
        estoqueService.createWarehouse("IT-REN-WH", "Depósito rename", WarehouseType.LOJA_FISICA);
        productRepository.save(Product.create("IT-REN-001", "Seda Alfafa", "Alfafa",
                List.of(ProductVariant.create("IT-REN-001-KS", List.of()))));
        productRepository.save(Product.create("IT-REN-KIT", "Kit rename", "Kits", List.of(),
                Pricing.of(null, null, new BigDecimal("30.00")), ProductType.KIT));
        estoqueService.adjustStock("IT-REN-001-KS", "IT-REN-WH", MovementType.ENTRADA,
                new BigDecimal("5"), "carga inicial", "it");
        jdbc.update("INSERT INTO product_kit_component (kit_sku, component_sku, quantity) VALUES (?, ?, 1)",
                "IT-REN-KIT", "IT-REN-001-KS");

        estoqueService.changeSku("IT-REN-001", "IT-REN-NOVO");
        estoqueService.changeSku("IT-REN-001-KS", "IT-REN-NOVO-KS");

        assertThat(productRepository.findBySku("IT-REN-NOVO")).isPresent();
        assertThat(productRepository.findBySku("IT-REN-001")).isEmpty();
        assertThat(productRepository.findByAnySku("IT-REN-NOVO-KS").orElseThrow().variants())
                .extracting(ProductVariant::sku).containsExactly("IT-REN-NOVO-KS");
        assertThat(count("stock_balance", "IT-REN-NOVO-KS")).isEqualTo(1);
        assertThat(count("stock_balance", "IT-REN-001-KS")).isZero();
        assertThat(count("stock_movement", "IT-REN-NOVO-KS")).isPositive();
        assertThat(count("stock_movement", "IT-REN-001-KS")).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT component_sku FROM product_kit_component WHERE kit_sku = 'IT-REN-KIT'", String.class))
                .isEqualTo("IT-REN-NOVO-KS");
    }

    private int count(String table, String sku) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE sku = ?", Integer.class, sku);
    }
}
