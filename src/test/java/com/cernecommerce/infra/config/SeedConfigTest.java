package com.cernecommerce.infra.config;

import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.PermissionUseCase;
import com.cernecommerce.core.ports.in.RoleUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Garante que o usuário {@code admin} seedado em dev receba as mesmas permissões de estoque
 * que {@link DevRoleBootstrapConfig} concede a ROLE_DEV — sem isso, o admin de teste local
 * recebe 403 inesperado em {@code /estoque/**} (C006).
 */
class SeedConfigTest {

    private final SeedConfig seedConfig = new SeedConfig();

    @Test
    void seedAll_grantsEstoquePermissionsToRoleAdmin() throws Exception {
        UserUseCase userUseCase = mock(UserUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        CashbackUseCase cashbackUseCase = mock(CashbackUseCase.class);
        when(userUseCase.findByUsername(anyString())).thenReturn(Optional.empty());

        CommandLineRunner runner = seedConfig.seedAll(userUseCase, roleUseCase, permissionUseCase,
                cashbackUseCase, "Admin@dev1", "User@dev1");
        runner.run();

        verify(permissionUseCase).createPermission("ESTOQUE_PRODUCT_READ");
        verify(permissionUseCase).createPermission("ESTOQUE_PRODUCT_MANAGE");
        verify(permissionUseCase).createPermission("ESTOQUE_WAREHOUSE_READ");
        verify(permissionUseCase).createPermission("ESTOQUE_WAREHOUSE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "ESTOQUE_PRODUCT_READ");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "ESTOQUE_PRODUCT_MANAGE");
        // EST-F019 — sem esta no seed, precificar responde 403 em dev, que é exatamente o
        // sintoma que EST-C001 já custou uma sessão de depuração no PDV_SALE_MANAGE.
        verify(permissionUseCase).createPermission("ESTOQUE_PRODUCT_PRICE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "ESTOQUE_PRODUCT_PRICE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "ESTOQUE_WAREHOUSE_READ");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "ESTOQUE_WAREHOUSE_MANAGE");
    }

    /**
     * {@code PDV_SALE_MANAGE} protege {@code POST /pdv/sessions/{id}/sales}, que é o caminho de
     * baixa automática de estoque. Sem ele no seed, o admin de dev toma 403 ao registrar venda
     * e o fluxo PDV → estoque fica inalcançável localmente (EST-C001).
     */
    @Test
    void seedAll_grantsPdvSaleManageToRoleAdmin() throws Exception {
        UserUseCase userUseCase = mock(UserUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        CashbackUseCase cashbackUseCase = mock(CashbackUseCase.class);
        when(userUseCase.findByUsername(anyString())).thenReturn(Optional.empty());

        CommandLineRunner runner = seedConfig.seedAll(userUseCase, roleUseCase, permissionUseCase,
                cashbackUseCase, "Admin@dev1", "User@dev1");
        runner.run();

        verify(permissionUseCase).createPermission("PDV_SALE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "PDV_SALE_MANAGE");
    }
}
