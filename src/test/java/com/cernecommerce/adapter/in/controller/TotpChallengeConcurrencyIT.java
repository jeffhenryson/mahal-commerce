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
 * Testa que requisições concorrentes completando o login 2FA com o mesmo challenge token
 * consomem o token exatamente uma vez — garantindo a segurança do CAS atômico
 * ({@code TotpChallengeTokenJpaRepository.markAsUsedIfAvailable}). O CAS do challenge token
 * acontece antes da validação do código TOTP em {@code TotpService.completeChallengeLogin},
 * então mesmo threads com o código correto devem falhar se perderem a corrida (C008).
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TotpChallengeConcurrencyIT {

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
    void concurrent_verify_with_same_challenge_token_succeeds_exactly_once() throws Exception {
        MockMvc mvc = mockMvc();
        String username = "totpchal_" + System.currentTimeMillis();
        String password = "Totp@Challenge1";

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
                        .content(String.format("{\"code\":\"%s\"}", generateCode(secret)))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Login com 2FA ativo — retorna challenge, não tokens.
        MvcResult challengeResult = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)))
                .andExpect(status().isOk())
                .andReturn();
        String challengeToken = om.readTree(challengeResult.getResponse().getContentAsString())
                .get("challengeToken").asText();
        String verifyCode = generateCode(secret);

        int threads = 5;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    String body = String.format("{\"challengeToken\":\"%s\",\"code\":\"%s\"}", challengeToken, verifyCode);
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
                .as("Exatamente uma conclusão de login 2FA deve ter sucesso com o mesmo challenge token")
                .isEqualTo(1);
        assertThat(failures.get())
                .as("As demais requisições devem falhar — challenge token já usado")
                .isEqualTo(threads - 1);
    }
}
