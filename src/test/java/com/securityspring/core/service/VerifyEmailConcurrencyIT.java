package com.securityspring.core.service;

import com.securityspring.core.domain.exception.email.EmailVerificationCodeExpiredException;
import com.securityspring.core.domain.exception.email.EmailVerificationCodeNotFoundException;
import com.securityspring.core.ports.in.UserUseCase;
import com.securityspring.infra.security.support.EmailVerificationTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa que duas requisições concorrentes com o mesmo código de verificação
 * ativam a conta exatamente uma vez — garantindo a segurança da operação CAS
 * ({@code markAsUsed}) no fluxo de verificação de email.
 *
 * <p>Sem a operação atômica {@code UPDATE ... WHERE used = false}, duas threads
 * poderiam passar pelo check de expiração e ambas ativariam a conta, o que pode
 * criar inconsistências caso haja lógica associada à ativação (ex: notificações,
 * créditos iniciais).</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VerifyEmailConcurrencyIT {

    @Autowired
    private UserUseCase userUseCase;

    @Autowired
    private EmailVerificationTestHelper verificationHelper;

    @Test
    void verify_email_concorrente_ativa_conta_exatamente_uma_vez() throws Exception {
        // Arrange — registra um novo usuário e captura o código em plaintext
        String suffix = String.valueOf(System.currentTimeMillis());
        String username = "concurrent_" + suffix;
        String email = username + "@test.com";
        userUseCase.registerUser(username, "Senha@123", email, List.of());
        String code = verificationHelper.getCodeForUsername(username);

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
                    start.await(); // espera o sinal para disparar todas ao mesmo tempo
                    userUseCase.verifyEmail(code);
                    successes.incrementAndGet();
                } catch (EmailVerificationCodeNotFoundException | EmailVerificationCodeExpiredException e) {
                    // Esperado para as threads que chegaram depois do CAS ser ganho pela primeira
                    failures.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        ready.await(); // espera todas as threads estarem prontas
        start.countDown(); // dispara todas simultaneamente

        for (Future<?> f : futures) f.get();
        executor.shutdown();

        // Assert — exatamente uma thread deve ter ativado a conta
        assertThat(successes.get())
                .as("Exatamente uma requisição deve ativar a conta")
                .isEqualTo(1);
        assertThat(failures.get())
                .as("As demais requisições devem receber código inválido/expirado")
                .isEqualTo(threads - 1);

        // A conta deve estar ativa após a verificação bem-sucedida
        var user = userUseCase.findByUsername(username);
        assertThat(user).isPresent();
        assertThat(user.get().isEnabled())
                .as("Conta deve estar habilitada após verificação")
                .isTrue();
        assertThat(user.get().isEmailVerified())
                .as("Email deve estar marcado como verificado")
                .isTrue();
    }
}
