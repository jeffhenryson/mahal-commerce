package com.cernecommerce.infra.config;

import com.cernecommerce.core.domain.model.cashback.CashbackScope;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.PermissionUseCase;
import com.cernecommerce.core.ports.in.RoleUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.List;

@Configuration
@Profile("dev")
public class SeedConfig {

    private static final Logger log = LoggerFactory.getLogger(SeedConfig.class);

    // Permissões do ADMIN: gestão de usuários, leitura de roles/permissões, auditoria de negócio.
    // ADMIN NÃO pode criar/deletar roles ou permissões — isso é exclusivo do DEV.
    private static final String[] ADMIN_PERMISSIONS = {
        "USER_CREATE", "USER_READ", "USER_UPDATE", "USER_DELETE", "USER_ROLE_ASSIGN", "USER_STATUS",
        "ROLE_READ", "ROLE_MANAGE_PERMISSIONS",
        "PERMISSION_READ",
        "AUDIT_READ",
        "ESTOQUE_PRODUCT_READ", "ESTOQUE_PRODUCT_MANAGE", "ESTOQUE_PRODUCT_PRICE_MANAGE",
        "ESTOQUE_WAREHOUSE_READ", "ESTOQUE_WAREHOUSE_MANAGE",
        "ESTOQUE_STOCK_MANAGE",
        "ESTOQUE_RESERVATION_READ",
        "ESTOQUE_KIT_MANAGE",
        "ESTOQUE_CATEGORY_MANAGE",
        "ESTOQUE_BRAND_MANAGE",
        "ESTOQUE_REPLENISHMENT_MANAGE",
        "ESTOQUE_ATTRIBUTE_MANAGE",
        "CRM_CUSTOMER_READ", "CRM_CUSTOMER_MANAGE", "CRM_CUSTOMER_LOOKUP", "CRM_CUSTOMER_EXPORT",
        "CASHBACK_RATE_MANAGE", "CASHBACK_READ",
        "COMPRAS_READ", "COMPRAS_RECEIPT_MANAGE", "ECOMMERCE_READ",
        "FINANCEIRO_READ", "FINANCEIRO_CASH_FLOW_MANAGE", "LOGISTICA_READ",
        "PDV_READ", "PDV_SALE_MANAGE", "PDV_SALE_DISCOUNT",
        "PDV_SESSION_MANAGE", "PDV_SESSION_CLOSE", "PDV_COMANDA_MANAGE",
        "ORDER_READ", "ORDER_FULFILL", "ORDER_CANCEL", "ORDER_REFUND"
    };

    // Permissões do cliente do marketplace (Fatia 8/9) — NÃO entram em ADMIN_PERMISSIONS,
    // ROLE_CUSTOMER é uma role separada com escopo mínimo (plano-pdv-marketplace.md §2.9).
    private static final String[] SHOP_CUSTOMER_PERMISSIONS = {
        "SHOP_CART_OWN", "SHOP_ORDER_OWN", "SHOP_CASHBACK_OWN"
    };

    // DEV_ONLY_PERMISSIONS e ROLE_DEV são gerenciados pelo DevRoleBootstrapConfig (todos os profiles).

    @Bean
    CommandLineRunner seedAll(UserUseCase userUseCase,
                              RoleUseCase roleUseCase,
                              PermissionUseCase permissionUseCase,
                              CashbackUseCase cashbackUseCase,
                              @Value("${seed.admin.password:Admin@dev1}") String adminPassword,
                              @Value("${seed.user.password:User@dev1}") String userPassword,
                              @Value("${seed.atendente.password:Atendente@dev1}") String atendentePassword) {
        return args -> {
            seedPermissions(permissionUseCase);
            seedRoles(roleUseCase);
            seedUsers(userUseCase, adminPassword, userPassword, atendentePassword);
            seedCashbackRate(cashbackUseCase);
        };
    }

    // Espelha o seed da V70 (taxa GLOBAL 3%) para o profile dev: aqui o schema nasce do ddl-auto a
    // partir das entities (spring.flyway.enabled=false), então a migration nunca roda e a taxa
    // GLOBAL da V70 jamais existiria sem isto — CashbackFlowIT (e o balcão em dev) ficariam sem
    // taxa aplicável.
    private void seedCashbackRate(CashbackUseCase cashbackUseCase) {
        try { cashbackUseCase.createRate(CashbackScope.GLOBAL, null, new BigDecimal("3.00"), null, null); }
        catch (Exception e) { log.debug("seed.cashbackRate.skip reason={}", e.getMessage()); }
    }

    private void seedPermissions(PermissionUseCase permissionUseCase) {
        for (String name : ADMIN_PERMISSIONS) {
            try { permissionUseCase.createPermission(name); }
            catch (Exception e) { log.debug("seed.permission.skip name={} reason={}", name, e.getMessage()); }
        }
        for (String name : SHOP_CUSTOMER_PERMISSIONS) {
            try { permissionUseCase.createPermission(name); }
            catch (Exception e) { log.debug("seed.permission.skip name={} reason={}", name, e.getMessage()); }
        }
        // DEV_ONLY_PERMISSIONS e ROLE_DEV são gerenciados pelo DevRoleBootstrapConfig (todos os profiles).
    }

    private void seedRoles(RoleUseCase roleUseCase) {
        for (String name : new String[]{"ROLE_ADMIN", "ROLE_USER", "ROLE_CUSTOMER", "ROLE_ATENDENTE"}) {
            try { roleUseCase.createRole(name); }
            catch (Exception e) { log.debug("seed.role.skip name={} reason={}", name, e.getMessage()); }
        }

        for (String perm : ADMIN_PERMISSIONS) {
            try { roleUseCase.assignPermission("ROLE_ADMIN", perm); }
            catch (Exception e) { log.debug("seed.role.assignPermission.skip role=ROLE_ADMIN perm={} reason={}", perm, e.getMessage()); }
        }

        try { roleUseCase.assignPermission("ROLE_USER", "USER_READ"); }
        catch (Exception e) { log.debug("seed.role.assignPermission.skip role=ROLE_USER perm=USER_READ reason={}", e.getMessage()); }

        for (String perm : SHOP_CUSTOMER_PERMISSIONS) {
            try { roleUseCase.assignPermission("ROLE_CUSTOMER", perm); }
            catch (Exception e) { log.debug("seed.role.assignPermission.skip role=ROLE_CUSTOMER perm={} reason={}", perm, e.getMessage()); }
        }
    }

    private void seedUsers(UserUseCase userUseCase, String adminPassword, String userPassword, String atendentePassword) {
        if (userUseCase.findByUsername("admin").isEmpty())
            userUseCase.createUser("admin", adminPassword, List.of("ROLE_ADMIN"));
        if (userUseCase.findByUsername("user").isEmpty())
            userUseCase.createUser("user", userPassword, List.of("ROLE_USER"));
        if (userUseCase.findByUsername("atendente").isEmpty())
            userUseCase.createUser("atendente", atendentePassword, List.of("ROLE_ATENDENTE"));
    }
}
