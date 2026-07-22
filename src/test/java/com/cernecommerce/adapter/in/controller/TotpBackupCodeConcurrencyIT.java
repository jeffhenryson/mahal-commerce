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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa que requisições concorrentes completando o login 2FA com o mesmo backup code (cada
 * uma com seu próprio challenge token) consomem o backup code exatamente uma vez — garantindo
 * a segurança do CAS atômico ({@code TotpBackupCodeJpaRepository.markAsUsedIfAvailable}) usado
 * por {@code TotpService.isValidBackupCode}.
 *
 * <p>Usa um challenge token distinto por thread (via login real repetido) para isolar a corrida
 * no backup code — se todas as threads compartilhassem o mesmo challenge token, o CAS do
 * challenge token (que acontece antes da validação do código em
 * {@code TotpService.completeChallengeLogin}) já serializaria as threads e nenhuma outra
 * chegaria a tentar consumir o backup code. Sem a atomicidade do backup code, duas requisições
 * com challenge tokens diferentes poderiam reutilizar o mesmo backup code (bypass de 2FA) (C008).</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TotpBackupCodeConcurrencyIT {

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

    private String generateCode(String secret) throws Exception {
        long counter = Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
        return codeGenerator.generate(secret, counter);
    }

    @Test
    void concurrent_challenge_completion_with_same_backup_code_succeeds_exactly_once() throws Exception {
        MockMvc mvc = mockMvc();
        String username = "totpbkp_" + System.currentTimeMillis();
        String password = "Totp@Backup1";

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

        MvcResult confirmResult = mvc.perform(post("/auth/2fa/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"code\":\"%s\"}", generateCode(secret)))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode confirmJson = om.readTree(confirmResult.getResponse().getContentAsString());
        String backupCode = confirmJson.get("backupCodes").get(0).asText();

        int threads = 5;

        // Um challenge token distinto por thread — cada login real (com 2FA já ativo) emite um
        // novo challenge token válido para o mesmo usuário.
        List<String> challengeTokens = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            MvcResult challengeResult = mvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)))
                    .andExpect(status().isOk())
                    .andReturn();
            challengeTokens.add(om.readTree(challengeResult.getResponse().getContentAsString())
                    .get("challengeToken").asText());
        }

        // Reseta o rate limiter novamente — o setup acima já consumiu boa parte do budget da
        // janela e a corrida em si não deve ser afetada por isso (não é o que este teste mede).
        if (rateLimiter instanceof InMemoryLoginRateLimiterAdapter rl) rl.reset();

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String challengeToken = challengeTokens.get(i);
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    String body = String.format("{\"challengeToken\":\"%s\",\"code\":\"%s\"}", challengeToken, backupCode);
                    MvcResult result = mvc.perform(post("/auth/2fa/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) successes.incrementAndGet();
                    else failures.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        ready.await();
        start.countDown();
        for (Future<?> f : futures) f.get();
        executor.shutdown();

        assertThat(successes.get())
                .as("Exatamente uma conclusão de login 2FA deve ter sucesso com o mesmo backup code")
                .isEqualTo(1);
        assertThat(failures.get())
                .as("As demais requisições devem falhar — backup code já usado (challenge tokens são todos válidos e distintos)")
                .isEqualTo(threads - 1);
    }
}
