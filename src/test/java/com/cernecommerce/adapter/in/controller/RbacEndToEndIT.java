package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.out.security.ratelimit.InMemoryLoginRateLimiterAdapter;
import com.cernecommerce.core.ports.in.RoleUseCase;
import com.cernecommerce.core.ports.out.ratelimit.LoginRateLimiterPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercita RBAC de ponta a ponta pelo pipeline real (C007): cria usuário via API, atribui uma
 * role via API, faz login real (sem authorities injetadas via {@code SecurityMockMvcRequestPostProcessors})
 * e usa o JWT emitido — com authorities carregadas do banco por {@code CustomUserDetailsService} —
 * para chamar um endpoint protegido. Todos os demais {@code *ControllerSecurityTest} injetam
 * authorities manualmente; este é o único que prova que o pipeline completo (senha → login →
 * JWT → authorities reais) funciona de ponta a ponta.
 *
 * <p>Reaproveita {@code PDV_READ} (permissão real, seedada pela migration V53) e o endpoint
 * {@code GET /pdv/sessions} como alvo — não precisa de nenhum endpoint/permissão novos.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RbacEndToEndIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RoleUseCase roleUseCase;

    @Autowired
    private LoginRateLimiterPort rateLimiter;

    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void resetRateLimiter() {
        if (rateLimiter instanceof InMemoryLoginRateLimiterAdapter rl) rl.reset();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String createUserViaApi(MockMvc mvc, String username, String password) throws Exception {
        mvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .with(user("admin_setup").authorities(new SimpleGrantedAuthority("USER_CREATE")))
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"roles\":[]}"))
                .andExpect(status().isCreated());
        return username;
    }

    private String loginAndGetAccessToken(MockMvc mvc, String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = om.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    @Test
    void user_with_role_and_permission_reaches_protected_endpoint_via_real_jwt() throws Exception {
        MockMvc mvc = mockMvc();
        String suffix = String.valueOf(System.currentTimeMillis());
        String roleName = "ROLE_RBAC_IT_" + suffix;
        String username = "rbac_it_ok_" + suffix;
        String password = "Secure@123";

        // Setup direto no banco: criação de role é ação de DEV, fora do escopo deste teste de RBAC de negócio.
        roleUseCase.createRole(roleName);

        // Atribui PDV_READ (permissão real, migration V53) à role — via API real, exige ROLE_MANAGE_PERMISSIONS.
        mvc.perform(post("/roles/{roleName}/permissions/{permissionName}", roleName, "PDV_READ")
                .with(user("admin_setup").authorities(new SimpleGrantedAuthority("ROLE_MANAGE_PERMISSIONS"))))
                .andExpect(status().isNoContent());

        createUserViaApi(mvc, username, password);

        // Atribui a role ao usuário — via API real, exige USER_ROLE_ASSIGN.
        mvc.perform(post("/users/{username}/roles/{roleName}", username, roleName)
                .with(user("admin_setup").authorities(new SimpleGrantedAuthority("USER_ROLE_ASSIGN"))))
                .andExpect(status().isNoContent());

        // Login real — nenhuma authority injetada, passa pelo pipeline completo (senha → JWT → CustomUserDetailsService).
        String accessToken = loginAndGetAccessToken(mvc, username, password);

        // Usa o JWT real para chamar um endpoint que exige PDV_READ.
        mvc.perform(get("/pdv/sessions").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void user_without_permission_gets_403_via_real_jwt() throws Exception {
        MockMvc mvc = mockMvc();
        String suffix = String.valueOf(System.currentTimeMillis());
        String username = "rbac_it_noperm_" + suffix;
        String password = "Secure@123";

        // Usuário criado sem nenhuma role/permissão de negócio.
        createUserViaApi(mvc, username, password);

        String accessToken = loginAndGetAccessToken(mvc, username, password);

        mvc.perform(get("/pdv/sessions").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }
}
