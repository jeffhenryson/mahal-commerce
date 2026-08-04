package com.cernecommerce.adapter.in.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.adapter.out.security.blocklist.InMemoryTokenBlocklistAdapter;
import com.cernecommerce.adapter.out.security.ratelimit.InMemoryLoginAttemptAdapter;
import com.cernecommerce.adapter.out.security.ratelimit.InMemoryLoginRateLimiterAdapter;
import com.cernecommerce.infra.security.support.RefreshTokenTestHelper;
import com.cernecommerce.infra.security.support.TestHashUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.DefaultCodeGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("dev")
public class UserProfileAndSessionsTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private RefreshTokenTestHelper refreshTokenTestHelper;
    @Autowired
    private InMemoryTokenBlocklistAdapter blocklistAdapter;
    @Autowired
    private InMemoryLoginAttemptAdapter loginAttemptAdapter;
    @Autowired
    private InMemoryLoginRateLimiterAdapter loginRateLimiterAdapter;

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();
    private final DefaultCodeGenerator codeGenerator = new DefaultCodeGenerator();

    private String generateTotpCode(String secret) throws Exception {
        long counter = Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
        return codeGenerator.generate(secret, counter);
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        blocklistAdapter.clearAll();
        loginAttemptAdapter.clearAll();
        loginRateLimiterAdapter.reset();
    }

    @Test
    void me_returns_own_profile_of_authenticated_user() throws Exception {
        mockMvc.perform(get("/users/me")
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void me_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void change_own_password_success_returns_204() throws Exception {
        // Create a dedicated test user to avoid state pollution across tests
        String uniqueUsername = "pwtest_" + System.currentTimeMillis();
        String createBody = String.format(
                "{\"username\":\"%s\",\"password\":\"Pass1@word\"}", uniqueUsername);

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated());

        // Change that user's password
        String changeBody = "{\"currentPassword\":\"Pass1@word\",\"newPassword\":\"NewPass@999\"}";
        mockMvc.perform(put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeBody)
                .with(user(uniqueUsername).authorities(
                        new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void change_own_password_with_real_backup_code_returns_204() throws Exception {
        // Regressão do C022: ChangePasswordRequest.totpCode tinha @Size(max = 8), rejeitando
        // todo backup code real (formato XXXX-XXXX-XXXX, 14 chars) antes de chegar na
        // validação de negócio — trocar senha com backup code estava sempre quebrado.
        String username = "pwbkp_" + System.currentTimeMillis();
        String password = "Pass1@word";

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated());

        MvcResult setupResult = mockMvc.perform(post("/auth/2fa/setup")
                        .with(user(username).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andReturn();
        String secret = om.readTree(setupResult.getResponse().getContentAsString()).get("secret").asText();

        MvcResult confirmResult = mockMvc.perform(post("/auth/2fa/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"code\":\"%s\"}", generateTotpCode(secret)))
                        .with(user(username).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andReturn();
        String backupCode = om.readTree(confirmResult.getResponse().getContentAsString())
                .get("backupCodes").get(0).asText();
        assertThat(backupCode).hasSize(14); // XXXX-XXXX-XXXX

        String changeBody = String.format(
                "{\"currentPassword\":\"%s\",\"newPassword\":\"NewPass@Backup1\",\"totpCode\":\"%s\"}",
                password, backupCode);
        mockMvc.perform(put("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody)
                        .with(user(username).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void change_own_password_wrong_current_returns_400() throws Exception {
        String body = "{\"currentPassword\":\"TOTALLY_WRONG\",\"newPassword\":\"NewPass@123\"}";
        mockMvc.perform(put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void change_own_password_new_password_too_short_returns_400() throws Exception {
        String body = "{\"currentPassword\":\"Admin@dev1\",\"newPassword\":\"short\"}";
        mockMvc.perform(put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void simple_logout_blocks_access_token_immediately() throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"User@dev1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = om.readTree(r.getResponse().getContentAsString());
        String accessToken = json.get("accessToken").asText();
        String refreshToken = json.get("refreshToken").asText();

        // Single-device logout: only the refresh token is sent
        mockMvc.perform(post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        // Access token must be blocklisted immediately after logout
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void password_change_blocks_previously_issued_access_token() throws Exception {
        // Create a dedicated user to avoid state pollution
        String username = "pwblock_" + System.currentTimeMillis();
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1@word\"}", username))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated());

        // Login to get an access token
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1@word\"}", username)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();

        // Change password with revokeOtherSessions=true — must invalidate all existing sessions
        mockMvc.perform(put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Pass1@word\",\"newPassword\":\"NewPass@999\",\"revokeOtherSessions\":true}")
                .with(user(username).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNoContent());

        // Old access token must now be blocked
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void password_change_without_revoke_keeps_existing_sessions_active() throws Exception {
        String username = "pwkeep_" + System.currentTimeMillis();
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1@word\"}", username))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated());

        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1@word\"}", username)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();

        // Change password with revokeOtherSessions=false — session must remain active
        mockMvc.perform(put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Pass1@word\",\"newPassword\":\"NewPass@999\",\"revokeOtherSessions\":false}")
                .with(user(username).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNoContent());

        // Existing access token must still be valid
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void patch_me_updates_own_username() throws Exception {
        String unique = "patchme_" + System.currentTimeMillis();
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1@word\"}", unique))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated());

        String newUsername = unique + "_v2";
        mockMvc.perform(patch("/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\"}", newUsername))
                .with(user(unique).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(newUsername));
    }

    /**
     * Mass assignment: {@code UserUpdateRequest} não tem campo {@code roles}/{@code authorities}/
     * {@code permissions} — o Jackson ignora silenciosamente campos desconhecidos no corpo (sem
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} configurado em lugar nenhum do projeto), então mesmo um
     * usuário comum enviando esses campos no PATCH da própria conta não consegue se autopromover.
     */
    @Test
    void patch_me_ignores_client_supplied_roles_field() throws Exception {
        String unique = "massassign_" + System.currentTimeMillis();
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1@word\"}", unique))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated());

        String beforeJson = mockMvc.perform(get("/users/me")
                .with(user(unique).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        java.util.List<String> rolesBefore = (java.util.List<String>) om.readValue(beforeJson, java.util.Map.class).get("roles");

        mockMvc.perform(patch("/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + unique
                        + "\",\"roles\":[\"ADMIN\"],\"authorities\":[\"USER_CREATE\"],\"permissions\":[\"USER_CREATE\"]}")
                .with(user(unique).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(rolesBefore));

        mockMvc.perform(get("/users/me")
                .with(user(unique).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(rolesBefore));
    }

    @Test
    void disable_user_returns_204_and_blocks_subsequent_authentication() throws Exception {
        String unique = "disabletest_" + System.currentTimeMillis();
        MvcResult created = mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1@word\"}", unique))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = om.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/users/" + id + "/disable")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_STATUS"))))
                .andExpect(status().isNoContent());

        // Disabled accounts are rejected with generic 401 (no information disclosure)
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1@word\"}", unique)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enable_user_returns_204_and_allows_login() throws Exception {
        String unique = "enabletest_" + System.currentTimeMillis();
        MvcResult created = mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1@word\"}", unique))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = om.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Disable first
        mockMvc.perform(put("/users/" + id + "/disable")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_STATUS"))))
                .andExpect(status().isNoContent());

        // Re-enable
        mockMvc.perform(put("/users/" + id + "/enable")
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_STATUS"))))
                .andExpect(status().isNoContent());

        // After re-enable, account exists and login attempt returns auth failure (not 403)
        // because the test user was created without email verification
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1@word\"}", unique)))
                .andExpect(status().isOk());
    }

    @Test
    void logout_all_sessions_revokes_tokens_and_returns_204() throws Exception {
        // Login as user (not admin, to avoid affecting other tests that use admin tokens)
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"User@dev1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = om.readTree(r.getResponse().getContentAsString());
        String accessToken = json.get("accessToken").asText();

        // Revoke all sessions — must succeed
        mockMvc.perform(delete("/auth/sessions")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // The same access token is now blocklisted — subsequent request must return 401
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }
}
