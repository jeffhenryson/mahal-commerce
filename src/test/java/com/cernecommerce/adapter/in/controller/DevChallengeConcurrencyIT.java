package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.out.security.ratelimit.InMemoryLoginRateLimiterAdapter;
import com.cernecommerce.core.ports.out.ratelimit.LoginRateLimiterPort;
import com.cernecommerce.core.ports.out.twofa.DevChallengeRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
 * Testa que requisições concorrentes completando a elevação DEV (duplo TOTP) com o mesmo
 * devToken consomem o token exatamente uma vez — garantindo a segurança do CAS atômico
 * ({@code DevChallengeTokenJpaRepository.consumeIfAvailable}). Sem essa atomicidade, duas
 * requisições poderiam ambas emitir um access token DEV-elevado a partir do mesmo devToken (C008).
 *
 * <p>Semeia o {@code DevChallengeToken} diretamente via {@link DevChallengeRepository} (o mesmo
 * bean real usado em produção) em vez de passar por {@code POST /auth/dev/first-code}, evitando
 * a fragilidade de depender de dois códigos TOTP em períodos consecutivos reais de 30s.
 * {@code POST /auth/dev/complete} não exige autenticação — apenas devToken + código válidos.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DevChallengeConcurrencyIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DevChallengeRepository devChallengeRepository;

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

    private String generateCode(String secret, long counter) throws Exception {
        return codeGenerator.generate(secret, counter);
    }

    @Test
    void concurrent_dev_complete_with_same_dev_token_succeeds_exactly_once() throws Exception {
        MockMvc mvc = mockMvc();
        String username = "devchal_" + System.currentTimeMillis();
        String password = "Dev@Challenge1";

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

        // Semeia o devToken diretamente com periodT = período atual - 1, satisfazendo a
        // exigência de "código consecutivo" sem depender de esperar um período TOTP real.
        long currentPeriod = Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
        String rawDevToken = UUID.randomUUID().toString();
        devChallengeRepository.save(username, rawDevToken, currentPeriod - 1,
                Instant.now().plus(90, ChronoUnit.SECONDS));
        String secondTotpCode = generateCode(secret, currentPeriod);

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
                    String body = String.format("{\"devToken\":\"%s\",\"totpCode\":\"%s\"}", rawDevToken, secondTotpCode);
                    MvcResult result = mvc.perform(post("/auth/dev/complete")
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
                .as("Exatamente uma elevação DEV deve ter sucesso com o mesmo devToken")
                .isEqualTo(1);
        assertThat(failures.get())
                .as("As demais requisições devem falhar — devToken já usado")
                .isEqualTo(threads - 1);
    }
}
