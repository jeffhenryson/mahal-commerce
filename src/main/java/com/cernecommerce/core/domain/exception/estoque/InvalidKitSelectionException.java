package com.cernecommerce.core.domain.exception.estoque;

/**
 * A escolha do cliente não fecha um kit válido (EST-F031). Uma classe só, com código, e não uma por
 * regra: são todas a mesma situação para quem chama — "volte ao montador e corrija o passo X" —, e
 * o {@link #code()} é o que a tela usa para dizer qual.
 */
public class InvalidKitSelectionException extends RuntimeException {

    public enum Reason {
        /** Kit inativo ou não visível neste canal. */
        KIT_NOT_AVAILABLE,
        /** Nenhum item escolhido. */
        KIT_EMPTY,
        /** Passo obrigatório sem escolha. */
        KIT_REQUIRED_STEP_MISSING,
        /** Passo informado não pertence a este kit. */
        KIT_STEP_NOT_FOUND,
        /** Mais escolhas do que o passo permite. */
        KIT_STEP_MAX_ITEMS_EXCEEDED,
        /** Produto não é da categoria do passo. */
        KIT_ITEM_NOT_IN_STEP_CATEGORY,
        /** Produto inativo, sem preço, ou é ele próprio um kit. */
        KIT_ITEM_NOT_SELLABLE
    }

    private final Reason reason;

    public InvalidKitSelectionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public String code() {
        return reason.name();
    }
}
