package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.SessionMenu;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import com.cernecommerce.core.domain.model.crm.LeadResolution;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM-C006 / PDV-F020 / PDV-F021 contra Postgres real: V127 (permissão de lead) e V128 (cardápio de
 * sessão, CHECKs de modo com SESSAO/ROSH_EXTRA, seeds) só existem no Flyway — o H2 do perfil dev
 * monta o schema pelas entidades. Habilitar com: {@code ENABLE_TC=true ./mvnw test}
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
class SessionMenuPostgresIT {

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
        r.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired SessionMenuUseCase sessionMenuUseCase;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired CrmUseCase crmUseCase;
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrations_grantLeadCreationToTheAttendant_andSeedTheSessionMenu() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id
                WHERE r.name = 'ROLE_ATENDENTE' AND p.name = 'CRM_LEAD_CREATE'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id
                WHERE r.name = 'ROLE_ATENDENTE' AND p.name IN ('CRM_CUSTOMER_MANAGE', 'PDV_SESSAO_MANAGE')
                """, Integer.class)).isZero();

        assertThat(sessionMenuUseCase.listTiers()).extracting(SessionTier::nome, t -> t.preco().intValue())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Tradicional", 25),
                        org.assertj.core.groups.Tuple.tuple("Premium", 30),
                        org.assertj.core.groups.Tuple.tuple("Sence", 40));
        SessionSettings settings = sessionMenuUseCase.getSettings();
        assertThat(settings.vasoPadraoCodigo()).isEqualTo("VASO_P");
        assertThat(settings.vasoGrandeCodigo()).isEqualTo("VASO_G");
        assertThat(settings.upgradeVasoGrandePreco()).isEqualByComparingTo("10.00");
    }

    @Test
    void resolveLead_matchesPhoneDigitsOnPostgres() {
        LeadResolution first = crmUseCase.resolveLead("Lead PG", "(83) 98888-7777", null, null, "PDV");
        LeadResolution again = crmUseCase.resolveLead("Lead PG", "83988887777", null, "529.982.247-25", "Mesa");

        assertThat(first.created()).isTrue();
        assertThat(again.created()).isFalse();
        assertThat(again.customer().id()).isEqualTo(first.customer().id());
        assertThat(again.customer().cpf()).isEqualTo("52998224725");
    }

    @Test
    void sessionFlow_persistsTheNewModes_andAllocatesAndReleasesSeededUtensils() {
        // O admin informa quantos utensílios tem antes da primeira sessão (seed nasce com 0).
        sessionMenuUseCase.listAssetTypes().forEach(t -> sessionMenuUseCase.updateAssetType(t.id(), t.nome(), 3,
                t.incluso(), true));
        sessionMenuUseCase.updateSettings(new SessionSettings("VASO_P", "VASO_G", new BigDecimal("10.00"), Set.of()));
        SessionTier premium = sessionMenuUseCase.listTiers().stream().filter(t -> t.nome().equals("Premium"))
                .findFirst().orElseThrow();

        estoqueUseCase.createWarehouse("LOUNGE-PG", "Lounge PG", WarehouseType.LOJA_FISICA);
        CashRegisterSession caixa = pdvUseCase.openSession("caixa-pg", BigDecimal.ZERO, "LOUNGE-PG");
        Comanda mesa = comandaUseCase.openComanda(caixa.id(), "Mesa 1", "caixa-pg");

        Comanda comSessao = comandaUseCase.addSession(mesa.id(), premium.id(), "Smynar Limão", false, "caixa-pg");
        Long sessaoId = comSessao.items().get(0).id();
        comandaUseCase.addRoshExtra(mesa.id(), sessaoId, null, "Nay Uva", "caixa-pg");

        SessionMenu emUso = sessionMenuUseCase.getMenu();
        assertThat(emUso.utensilios()).filteredOn(a -> a.tipo().codigo().equals("VASO_P"))
                .singleElement().satisfies(a -> assertThat(a.disponivel()).isEqualTo(2));
        assertThat(emUso.utensilios()).filteredOn(a -> a.tipo().incluso())
                .allSatisfy(a -> assertThat(a.emUso()).isEqualTo(1));

        Order order = comandaUseCase.closeComanda(mesa.id(),
                List.of(new PaymentCommand(PaymentMethod.PIX, new BigDecimal("60.00"), null)), null, false, null,
                "caixa-pg");

        assertThat(order.items()).extracting(i -> i.mode())
                .containsExactlyInAnyOrder(ConsumptionMode.SESSAO, ConsumptionMode.ROSH_EXTRA);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_item WHERE order_id = ? AND mode IN ('SESSAO','ROSH_EXTRA')",
                Integer.class, order.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM comanda_session_asset WHERE comanda_item_id = ? AND liberado_em IS NULL",
                Integer.class, sessaoId)).isZero();
        assertThat(sessionMenuUseCase.getMenu().utensilios()).allSatisfy(a -> assertThat(a.emUso()).isZero());
    }
}
