package com.cernecommerce.core.domain;

/**
 * Centraliza a política de senha do domínio.
 *
 * <p>
 * Tanto a validação no service quanto as anotações de Bean Validation nos DTOs
 * devem
 * referenciar as constantes desta classe, garantindo que uma mudança de
 * política
 * (ex: exigir 12 chars ou proibir sequências) seja feita em um único lugar.
 * </p>
 *
 * <p>
 * Regras atuais:
 * <ul>
 * <li>Mínimo de 8 caracteres</li>
 * <li>Máximo de 120 caracteres</li>
 * <li>Ao menos uma letra maiúscula</li>
 * <li>Ao menos uma letra minúscula</li>
 * <li>Ao menos um dígito</li>
 * <li>Ao menos um caractere especial (não alfanumérico)</li>
 * </ul>
 */
public final class PasswordPolicy {

        /** Tamanho mínimo da senha em caracteres. */
        public static final int MIN_LENGTH = 8;

        /** Tamanho máximo da senha em caracteres. */
        public static final int MAX_LENGTH = 120;

        /**
         * Expressão regular de complexidade de senha.
         * Exige ao menos: uma maiúscula, uma minúscula, um dígito e um caractere
         * especial.
         * Usada em {@code @Pattern} nos DTOs e em {@link #isValid(String)} no service.
         */
        public static final String COMPLEXITY_REGEXP = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$";

        public static final String COMPLEXITY_MESSAGE = "A senha deve conter ao menos uma letra maiúscula, uma minúscula, um dígito e um caractere especial";

        private PasswordPolicy() {
        }

        /** Retorna {@code true} se a senha satisfaz todas as regras de política. */
        public static boolean isValid(String password) {
                return password != null
                                && password.length() >= MIN_LENGTH
                                && password.length() <= MAX_LENGTH
                                && password.matches(COMPLEXITY_REGEXP);
        }
}
