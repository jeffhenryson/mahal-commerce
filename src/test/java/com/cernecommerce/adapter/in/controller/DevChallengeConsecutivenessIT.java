package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.out.security.ratelimit.InMemoryLoginRateLimiterAdapter;
import com.cernecommerce.core.ports.out.ratelimit.LoginRateLimiterPort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa a regra central de segurança da elevação DEV — {@code TotpService.completeDevChallenge}
 * só concede o access token elevado quando o segundo código TOTP pertence ao período
 * IMEDIATAMENTE seguinte ({@code currentPeriod == challenge.periodT() + 1}) ao do primeiro
 * código. Isso prova posse contínua do dispositivo TOTP, não apenas um código isolado válido.
 *
 * <p>Diferente de {@link DevChallengeConcurrencyIT}, que semeia o {@code DevChallengeToken}
 * diretamente no repositório (contornando de propósito a checagem de consecutividade para
 * isolar o teste de concorrência), esta classe passa pelos dois endpoints reais —
 * {@code POST /auth/dev/first-code} e {@code POST /auth/dev/complete} — com códigos TOTP
 * gerados de verdade a partir do mesmo segredo, cobrindo o caminho que nenhum teste
 * existente exercitava ponta a ponta.</p>
 *
 * <p><b>Sobre como o "período atual" é simulado:</b> {@code TotpService} não expõe nenhum
 * {@code Clock} injetável — tanto {@code issueDevFirstCode} quanto {@code completeDevChallenge}
 * calculam o período a partir de {@code Instant.now()} real no momento em que a requisição
 * chega ao servidor, e não a partir do counter usado para gerar o código TOTP recebido. Ou
 * seja, gerar os dois códigos com {@code counter} e {@code counter + 1} a partir do mesmo
 * {@code System.currentTimeMillis()} capturado uma única vez NÃO é suficiente para o caminho
 * feliz: se as duas chamadas MockMvc acontecerem dentro da mesma janela real de 30s (o que é
 * o caso normal em um teste rápido), {@code currentPeriod} vai continuar igual a
 * {@code challenge.periodT()} e a exceção de não-consecutividade dispara de qualquer forma,
 * independente do código enviado. Por isso o teste de caminho feliz aguarda de verdade
 * (via {@code Thread.sleep}) a passagem real para o próximo período de 30s antes de gerar e
 * enviar o segundo código — o único mecanismo determinístico possível sem tocar em
 * {@code TotpService.java} (produção) para injetar um {@code Clock} mockável.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DevChallengeConsecutivenessIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private LoginRateLimiterPort rateLimiter;

    private final ObjectMapper om = new ObjectMapper();
    private final DefaultCodeGenerator codeGenerator = new DefaultCodeGenerator();

    @BeforeEach
    void resetRateLimiter() {
        if (rateLimiter instanceof InMemoryLoginRateLimiterAdapter rl) rl.reset();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String generateCode(String secret, long period) throws Exception {
        return codeGenerator.generate(secret, period);
    }

    /**
     * Cria um usuário, faz login, ativa 2FA com um segredo real e retorna o par
     * [username, accessToken, secret] já pronto para exercitar a elevação DEV.
     */
    private String[] createUserWithTotpEnabled(MockMvc mvc, String usernamePrefix) throws Exception {
        String username = usernamePrefix + System.currentTimeMillis();
        String password = "Dev@Consecutive1";

        mvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password))
                .with(user("admin_setup").authorities(new SimpleGrantedAuthority("USER_CREATE"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = om.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();

        MvcResult setupResult = mvc.perform(post("/auth/2fa/setup")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String secret = om.readTree(setupResult.getResponse().getContentAsString()).get("secret").asText();

        mvc.perform(post("/auth/2fa/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"code\":\"%s\"}",
                                generateCode(secret, Math.floorDiv(System.currentTimeMillis() / 1000L, 30L))))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        return new String[] { username, accessToken, secret };
    }

    @Test
    void dev_elevation_with_two_real_consecutive_totp_periods_succeeds() throws Exception {
        MockMvc mvc = mockMvc();
        String[] fixture = createUserWithTotpEnabled(mvc, "devconsec_ok_");
        String username = fixture[0];
        String secret = fixture[2];

        // Etapa 1 — primeiro código do período atual, autenticado como ROLE_DEV.
        long periodBeforeFirst = Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
        String firstCode = generateCode(secret, periodBeforeFirst);
        MvcResult firstCodeResult = mvc.perform(post("/auth/dev/first-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"totpCode\":\"%s\"}", firstCode))
                        .with(user(username).authorities(new SimpleGrantedAuthority("ROLE_DEV"))))
                .andExpect(status().isOk())
                .andReturn();
        String devToken = om.readTree(firstCodeResult.getResponse().getContentAsString()).get("devToken").asText();
        assertThat(devToken).isNotBlank();

        // Aguarda de verdade a passagem real para o próximo período TOTP de 30s — ver
        // javadoc da classe sobre por que counter+1 sem esperar não basta aqui.
        long periodAfterFirst = Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
        long nextPeriodStartMillis = (periodAfterFirst + 1) * 30_000L;
        long waitMillis = nextPeriodStartMillis - System.currentTimeMillis() + 300L; // margem de segurança
        Thread.sleep(Math.max(waitMillis, 0));

        // Etapa 2 — segundo código gerado a partir do período real em que a requisição
        // efetivamente será processada, agora sim o período imediatamente seguinte ao primeiro.
        long periodAtComplete = Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
        String secondCode = generateCode(secret, periodAtComplete);

        MvcResult completeResult = mvc.perform(post("/auth/dev/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"devToken\":\"%s\",\"totpCode\":\"%s\"}", devToken, secondCode)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = om.readTree(completeResult.getResponse().getContentAsString());
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
    }

    @Test
    void dev_elevation_with_second_code_from_same_period_fails_as_not_consecutive() throws Exception {
        MockMvc mvc = mockMvc();
        String[] fixture = createUserWithTotpEnabled(mvc, "devconsec_same_");
        String username = fixture[0];
        String secret = fixture[2];

        // Etapa 1 — primeiro código do período atual.
        long periodBeforeFirst = Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
        String firstCode = generateCode(secret, periodBeforeFirst);
        MvcResult firstCodeResult = mvc.perform(post("/auth/dev/first-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"totpCode\":\"%s\"}", firstCode))
                        .with(user(username).authorities(new SimpleGrantedAuthority("ROLE_DEV"))))
                .andExpect(status().isOk())
                .andReturn();
        String devToken = om.readTree(firstCodeResult.getResponse().getContentAsString()).get("devToken").asText();

        // Etapa 2 — reenvia o MESMO código imediatamente, sem esperar o próximo período.
        // Como completeDevChallenge calcula currentPeriod a partir do relógio real da
        // requisição, currentPeriod ainda é igual a challenge.periodT() (não periodT()+1)
        // nesse instante — reprovado pela checagem de consecutividade ANTES de validar o
        // código em si, então nenhuma espera real é necessária para este caso.
        mvc.perform(post("/auth/dev/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"devToken\":\"%s\",\"totpCode\":\"%s\"}", devToken, firstCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TOTP_NOT_CONSECUTIVE"));
    }

    @Test
    void dev_elevation_with_second_code_skipping_a_period_fails_as_not_consecutive() throws Exception {
        MockMvc mvc = mockMvc();
        String[] fixture = createUserWithTotpEnabled(mvc, "devconsec_skip_");
        String username = fixture[0];
        String secret = fixture[2];

        // Etapa 1 — primeiro código do período atual.
        long periodBeforeFirst = Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
        String firstCode = generateCode(secret, periodBeforeFirst);
        MvcResult firstCodeResult = mvc.perform(post("/auth/dev/first-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"totpCode\":\"%s\"}", firstCode))
                        .with(user(username).authorities(new SimpleGrantedAuthority("ROLE_DEV"))))
                .andExpect(status().isOk())
                .andReturn();
        String devToken = om.readTree(firstCodeResult.getResponse().getContentAsString()).get("devToken").asText();

        // Segundo código de um período dois à frente (pulando um) em vez do imediatamente
        // seguinte — código válido em si (dentro da janela de tolerância do CodeVerifier),
        // mas continua reprovando a checagem de consecutividade em completeDevChallenge.
        String skippedCode = generateCode(secret, periodBeforeFirst + 2);
        mvc.perform(post("/auth/dev/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"devToken\":\"%s\",\"totpCode\":\"%s\"}", devToken, skippedCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TOTP_NOT_CONSECUTIVE"));
    }
}
