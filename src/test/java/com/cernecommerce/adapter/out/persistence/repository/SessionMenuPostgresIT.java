package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.SessionMenu;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionStatus;
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

        // PDV-F023/F027 — o status mora nas colunas da V132, com os CHECKs de verdade (V135: aguardando
        // pagamento ainda sem tempo de mesa).
        assertThat(jdbc.queryForObject("SELECT session_status FROM comanda_item WHERE id = ?", String.class,
                sessaoId)).isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(jdbc.queryForObject("SELECT started_at IS NULL FROM comanda_item WHERE id = ?", Boolean.class,
                sessaoId)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM comanda_item WHERE linked_item_id = ? AND session_status = 'NA_FILA' "
                        + "AND started_at IS NULL", Integer.class, sessaoId)).isEqualTo(1);

        // Pagar leva ao preparo; o rosh extra, cobrado junto, continua na fila.
        Long roshId = comandaUseCase.getComanda(mesa.id()).items().stream()
                .filter(i -> sessaoId.equals(i.linkedItemId())).findFirst().orElseThrow().id();
        Order order = comandaUseCase.closeComanda(mesa.id(),
                List.of(new PaymentCommand(PaymentMethod.PIX, new BigDecimal("60.00"), null)), null, false,
                List.of(sessaoId, roshId), "caixa-pg");
        assertThat(jdbc.queryForObject("SELECT session_status FROM comanda_item WHERE id = ? AND started_at IS NOT NULL",
                String.class, sessaoId)).isEqualTo("PREPARANDO");

        // Encerrar a mesa exige tudo recolhido: recolher a sessão promove o rosh, que é recolhido em seguida.
        comandaUseCase.updateSessionStatus(mesa.id(), sessaoId, SessionStatus.RECOLHIDO, "caixa-pg");
        comandaUseCase.updateSessionStatus(mesa.id(), roshId, SessionStatus.RECOLHIDO, "caixa-pg");
        comandaUseCase.finishComanda(mesa.id(), "caixa-pg");

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

    /** PDV-F024 — V133: adicional com snapshot, rosh duplo atômico e carvão até a linha do pedido. */
    @Test
    void duploWithAddonAndCharcoal_persistsOnPostgres_andThePartialCloseKeepsTheTableOpen() {
        sessionMenuUseCase.listAssetTypes().forEach(t -> sessionMenuUseCase.updateAssetType(t.id(), t.nome(), 3,
                t.incluso(), true));
        sessionMenuUseCase.updateSettings(new SessionSettings("VASO_P", "VASO_G", new BigDecimal("10.00"), Set.of()));
        SessionTier premium = sessionMenuUseCase.listTiers().stream().filter(t -> t.nome().equals("Premium"))
                .findFirst().orElseThrow();
        SessionAddon filtro = sessionMenuUseCase.getMenu().adicionais().stream()
                .filter(a -> a.nome().equals("Filtro de gelo")).findFirst().orElseThrow();
        estoqueUseCase.createWarehouse("LOUNGE-PG2", "Lounge PG 2", WarehouseType.LOJA_FISICA);
        CashRegisterSession caixa = pdvUseCase.openSession("caixa-pg2", BigDecimal.ZERO, "LOUNGE-PG2");
        Comanda mesa = comandaUseCase.openComanda(caixa.id(), "Mesa 2", "caixa-pg2");

        Comanda comDuplo = comandaUseCase.addSession(mesa.id(), new ComandaUseCase.AddSessionCommand(premium.id(),
                "Smynar Limão", false, Charcoal.JUMBO, List.of(filtro.id()), true, "Nay Uva", null), "caixa-pg2");

        ComandaItem sessao = comDuplo.items().stream().filter(i -> i.mode() == ConsumptionMode.SESSAO)
                .findFirst().orElseThrow();
        ComandaItem rosh = comDuplo.items().stream().filter(i -> i.mode() == ConsumptionMode.ROSH_EXTRA)
                .findFirst().orElseThrow();
        assertThat(sessao.unitPrice()).isEqualByComparingTo("35.00");
        assertThat(jdbc.queryForObject("SELECT preco FROM comanda_item_addon WHERE comanda_item_id = ?",
                BigDecimal.class, sessao.id())).isEqualByComparingTo("5.00");
        assertThat(jdbc.queryForObject("SELECT charcoal FROM comanda_item WHERE id = ?", String.class, sessao.id()))
                .isEqualTo("JUMBO");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM comanda_item WHERE id = ? AND courtesy AND unit_price = 0 "
                        + "AND linked_item_id = ? AND session_status = 'NA_FILA'",
                Integer.class, rosh.id(), sessao.id())).isEqualTo(1);

        Order order = comandaUseCase.closeComanda(mesa.id(),
                List.of(new PaymentCommand(PaymentMethod.PIX, new BigDecimal("35.00"), null)), null, true,
                List.of(sessao.id(), rosh.id()), "caixa-pg2");

        assertThat(order.serviceFeeAmount()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject(
                "SELECT charcoal FROM order_item WHERE order_id = ? AND mode = 'SESSAO'", String.class, order.id()))
                .isEqualTo("JUMBO");
        assertThat(comandaUseCase.getComanda(mesa.id()).status()).isEqualTo(ComandaStatus.ABERTA);

        // Recolhe a sessão (promove o rosh) e o rosh, e encerra: utensílios de volta para os outros testes.
        comandaUseCase.updateSessionStatus(mesa.id(), sessao.id(), SessionStatus.RECOLHIDO, "caixa-pg2");
        comandaUseCase.updateSessionStatus(mesa.id(), rosh.id(), SessionStatus.RECOLHIDO, "caixa-pg2");
        Comanda encerrada = comandaUseCase.finishComanda(mesa.id(), "caixa-pg2");
        assertThat(encerrada.status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM comanda_session_asset WHERE comanda_item_id = ? AND liberado_em IS NULL",
                Integer.class, sessao.id())).isZero();
    }
}
