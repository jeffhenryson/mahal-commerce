package com.cernecommerce.infra.security.support;

import com.cernecommerce.adapter.out.email.LoggingEmailAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Helper para testes de integração — recupera o código de verificação em texto puro
 * diretamente do LoggingEmailAdapter (que o retém antes do hash ser computado).
 *
 * Não use o banco para isso: a tabela armazena apenas o SHA-256 do código, não o valor
 * original, portanto uma query retornaria o hash — inútil para chamar o endpoint de verificação.
 */
@Component
@Profile("dev")
public class EmailVerificationTestHelper {

    private final LoggingEmailAdapter loggingEmailAdapter;

    public EmailVerificationTestHelper(LoggingEmailAdapter loggingEmailAdapter) {
        this.loggingEmailAdapter = loggingEmailAdapter;
    }

    public String getCodeForUsername(String username) {
        String code = loggingEmailAdapter.getLastCodeForUsername(username);
        if (code == null) {
            throw new IllegalStateException("No verification code captured for username: " + username
                    + ". Ensure the registration endpoint was called before retrieving the code.");
        }
        return code;
    }
}
