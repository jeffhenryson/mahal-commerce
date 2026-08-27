package com.cernecommerce.adapter.in.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.DefaultCodeGenerator;
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

/**
 * IT end-to-end do fluxo TOTP:
 * ativar 2FA → login com challenge → completar com código real → receber tokens
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class TotpFlowIT {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();
    private final DefaultCodeGenerator codeGenerator = new DefaultCodeGenerator();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void totp_setup_confirm_and_challenge_login_completes_successfully() throws Exception {
        String username = "totp_" + System.currentTimeMillis();
        String password = "Totp@Secret1";

        // 1. Criar usuário como admin
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated());

        // 2. Login normal (sem 2FA) — obter access token
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = om.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // 3. Iniciar configuração do 2FA
        MvcResult setupResult = mockMvc.perform(post("/auth/2fa/setup")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String secret = om.readTree(setupResult.getResponse().getContentAsString())
                .get("secret").asText();
        assertThat(secret).isNotBlank();

        // 4. Confirmar 2FA com código TOTP real gerado a partir do secret
        String confirmCode = generateCode(secret);
        mockMvc.perform(post("/auth/2fa/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"code\":\"%s\"}", confirmCode))
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupCodes").isArray());

        // 5. Login novamente — deve retornar challenge de 2FA
        MvcResult challengeLogin = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode challengeBody = om.readTree(challengeLogin.getResponse().getContentAsString());
        assertThat(challengeBody.get("status").asText()).isEqualTo("PENDING_2FA");
        String challengeToken = challengeBody.get("challengeToken").asText();
        assertThat(challengeToken).isNotBlank();

        // 6. Completar challenge com código TOTP fresco
        String verifyCode = generateCode(secret);
        MvcResult verifyResult = mockMvc.perform(post("/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"challengeToken\":\"%s\",\"code\":\"%s\"}", challengeToken, verifyCode)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = om.readTree(verifyResult.getResponse().getContentAsString());
        assertThat(tokens.has("accessToken")).isTrue();
        assertThat(tokens.has("refreshToken")).isTrue();

        // 7. Access token obtido via 2FA funciona para recursos protegidos
        String newAccessToken = tokens.get("accessToken").asText();
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    void totp_challenge_with_wrong_code_returns_400() throws Exception {
        String username = "totp_wrong_" + System.currentTimeMillis();
        String password = "Totp@Wrong99";

        // Criar usuário e ativar 2FA
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password))
                .with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = om.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        MvcResult setupResult = mockMvc.perform(post("/auth/2fa/setup")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String secret = om.readTree(setupResult.getResponse().getContentAsString())
                .get("secret").asText();

        String confirmCode = generateCode(secret);
        mockMvc.perform(post("/auth/2fa/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"code\":\"%s\"}", confirmCode))
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Login com 2FA → challenge
        MvcResult challengeLogin = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)))
                .andExpect(status().isOk())
                .andReturn();
        String challengeToken = om.readTree(challengeLogin.getResponse().getContentAsString())
                .get("challengeToken").asText();

        // Código errado → 400
        mockMvc.perform(post("/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"challengeToken\":\"%s\",\"code\":\"000000\"}", challengeToken)))
                .andExpect(status().isBadRequest());
    }

    private String generateCode(String secret) throws Exception {
        long counter = Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
        return codeGenerator.generate(secret, counter);
    }
}
