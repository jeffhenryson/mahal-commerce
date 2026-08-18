package com.cernecommerce.core.domain.exception.estoque;

public class DraftLimitReachedException extends RuntimeException {
    public DraftLimitReachedException() {
        super("Limite de 5 rascunhos atingido — publique ou remova um rascunho antes de criar outro");
    }
}
