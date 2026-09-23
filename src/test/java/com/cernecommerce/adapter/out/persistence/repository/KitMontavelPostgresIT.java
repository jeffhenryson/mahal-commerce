package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.ecommerce.Cart;
import com.cernecommerce.core.domain.model.ecommerce.CartItem;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.model.estoque.KitTemplate;
import com.cernecommerce.core.domain.model.estoque.KitTemplateStep;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.out.estoque.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EST-F031 / ECM-F008 / PDV-F019 contra Postgres real: a V126 (índice único PARCIAL de cart_item,
 * CHECKs de "tudo ou nada" dos campos de kit) só existe no Flyway — o H2 do perfil dev monta o
 * schema pelas entidades, sem nada disso. Habilitar com: {@code ENABLE_TC=true ./mvnw test}
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
class KitMontavelPostgresIT {

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
        // Ver ProductRepositoryPostgresIT: o dev-schema.sql é H2 e derrubaria o contexto aqui.
        r.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired KitTemplateRepositoryImpl kitTemplateRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CartRepositoryImpl cartRepository;
    @Autowired CrmUseCase crmUseCase;
    @Autowired JdbcTemplate jdbc;

    @Test
    void template_updateKeepsIdsOfExistingSteps() {
        Long bag = categoryRepository.save(Category.create("IT Kit Bag")).id();
        Long seda = categoryRepository.save(Category.create("IT Kit Seda")).id();
        KitTemplate created = kitTemplateRepository.save(new KitTemplate(null, "IT Kit Mahal", null, null,
                new BigDecimal("10"), true, true, true, List.of(
                        new KitTemplateStep(null, "Bag", 0, bag, true, 1),
                        new KitTemplateStep(null, "Seda", 1, seda, true, 2))));
        KitTemplateStep bagStep = created.steps().get(0);

        // Admin edita: mantém o passo Bag (com id), remove Seda, cria Isqueiro.
        KitTemplate updated = kitTemplateRepository.save(new KitTemplate(created.id(), "IT Kit Mahal", "novo", null,
                new BigDecimal("15"), true, true, false, List.of(
                        new KitTemplateStep(bagStep.id(), "Bag grande", 0, bag, true, 1),
                        new KitTemplateStep(null, "Isqueiro", 1, seda, false, 1))));

        assertThat(updated.steps()).extracting(KitTemplateStep::name).containsExactly("Bag grande", "Isqueiro");
        assertThat(updated.steps().get(0).id()).isEqualTo(bagStep.id());
        assertThat(kitTemplateRepository.findByNameIgnoreCase("it kit mahal")).isPresent();
        assertThat(kitTemplateRepository.findById(created.id()).orElseThrow().discountPercent())
                .isEqualByComparingTo("15");
    }

    @Test
    void cart_sameSkuMayBeLooseAndInsideKits_butLooseStaysUnique() {
        Long customerId = crmUseCase.createCustomer("IT Kit", "11999990000", "it-kit@x.com", null, "MARKETPLACE").id();
        cartRepository.upsertItem(customerId, "SEDA-01", BigDecimal.ONE);
        cartRepository.addKitBundle(customerId, List.of(new CartItem("SEDA-01", BigDecimal.ONE, "b-1", 7L, 20L)));
        Cart cart = cartRepository.addKitBundle(customerId,
                List.of(new CartItem("SEDA-01", BigDecimal.ONE, "b-2", 7L, 20L)));

        assertThat(cart.items()).extracting(CartItem::kitBundleId).containsExactlyInAnyOrder(null, "b-1", "b-2");

        // O upsert avulso mexe só na linha avulsa.
        cart = cartRepository.upsertItem(customerId, "SEDA-01", new BigDecimal("3"));
        assertThat(cart.items()).filteredOn(i -> !i.inKit()).singleElement()
                .extracting(CartItem::quantity).satisfies(q -> assertThat(q).isEqualByComparingTo("3"));

        assertThat(cartRepository.removeKitBundle(customerId, "b-1")).isTrue();
        assertThat(cartRepository.findByCustomerId(customerId).orElseThrow().items()).hasSize(2);

        // O índice parcial ainda recusa uma segunda linha AVULSA do mesmo SKU.
        Long cartId = jdbc.queryForObject("SELECT id FROM cart WHERE customer_id = ?", Long.class, customerId);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO cart_item (cart_id, sku, quantity) VALUES (?, 'SEDA-01', 1)",
                cartId)).isInstanceOf(DataIntegrityViolationException.class);
        // E o CHECK recusa linha de kit pela metade.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO cart_item (cart_id, sku, quantity, kit_bundle_id) VALUES (?, 'X', 1, 'b-9')", cartId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void comandaItem_hasKitColumnsAndPermissionIsSeeded() {
        List<String> cols = new ArrayList<>(jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'comanda_item' "
                        + "AND column_name LIKE 'kit_%'", String.class));
        assertThat(cols).containsExactlyInAnyOrder("kit_bundle_id", "kit_template_id", "kit_discount_amount");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permissions WHERE name = 'ESTOQUE_KIT_TEMPLATE_MANAGE'",
                Integer.class)).isEqualTo(1);
    }
}
