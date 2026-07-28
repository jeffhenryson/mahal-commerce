package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Sessão de caixa do balcão (PDV-F001): abertura → vendas e movimentações → fechamento com
 * conferência.
 *
 * <h2>Espelha o balanço de inventário, de propósito</h2>
 * <p>O fechamento de caixa é o mesmo problema do {@code StockCount}, com dinheiro no lugar de
 * mercadoria: confronta-se o <b>contado</b> com o <b>esperado</b>, carimba-se a divergência e
 * <b>fecha-se mesmo assim</b>. Divergência não é erro de validação — é o achado do fechamento, e
 * bloquear o encerramento por causa dela só produziria caixas que nunca fecham. Reusar o vocabulário
 * ({@link #diverges()}) poupa decisão e faz o sistema ser previsível para quem opera os dois.</p>
 *
 * <h2>Uma sessão aberta por operador</h2>
 * <p>Garantido por índice parcial único no schema (V66), não só aqui. O domínio é a primeira
 * barreira; o banco é a que sobrevive a duas requisições simultâneas e a script de correção.</p>
 *
 * @param warehouseCode depósito de onde sai a mercadoria vendida neste caixa. Fica na sessão, e não
 *        no request de cada venda, porque é isso que impede o operador de baixar estoque de um
 *        depósito que não é o dele (PDV-C004).
 * @param expectedAmount o que deveria haver na gaveta, derivado de abertura + vendas − sangrias +
 *        suprimentos. Só existe depois do fechamento.
 * @param differenceAmount {@code countedAmount − expectedAmount}. <b>Negativo significa falta</b>, e
 *        é um número legítimo — não uma violação.
 */
public record CashRegisterSession(
        Long id,
        String operator,
        Instant openedAt,
        BigDecimal openingAmount,
        String warehouseCode,
        Instant closedAt,
        String closedBy,
        BigDecimal expectedAmount,
        BigDecimal countedAmount,
        BigDecimal differenceAmount,
        Status status) {

    public enum Status { OPEN, CLOSED }

    public CashRegisterSession {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator é obrigatório");
        }
        if (openedAt == null) {
            throw new IllegalArgumentException("openedAt é obrigatório");
        }
        if (openingAmount == null || openingAmount.signum() < 0) {
            throw new IllegalArgumentException("openingAmount não pode ser negativo");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório");
        }
        // Estado e carimbos não podem discordar — é a invariante que o CHECK da V66 espelha.
        boolean closed = status == Status.CLOSED;
        if (closed != (closedAt != null)) {
            throw new IllegalArgumentException(
                    "status CLOSED e closedAt têm que coexistir: status=" + status + ", closedAt=" + closedAt);
        }
        if (closed != (countedAmount != null)) {
            throw new IllegalArgumentException(
                    "sessão fechada exige countedAmount, e sessão aberta não tem o que contar");
        }
        if (countedAmount != null && countedAmount.signum() < 0) {
            throw new IllegalArgumentException("countedAmount não pode ser negativo");
        }
        // differenceAmount é derivado; se vier informado, tem que bater. Falta no caixa é negativa e
        // continua válida — o que não pode é o número contar uma história diferente dos outros dois.
        if (differenceAmount != null && countedAmount != null && expectedAmount != null
                && differenceAmount.compareTo(countedAmount.subtract(expectedAmount)) != 0) {
            throw new IllegalArgumentException("differenceAmount deve ser countedAmount - expectedAmount: "
                    + "esperado " + countedAmount.subtract(expectedAmount) + ", recebido " + differenceAmount);
        }
    }

    /** Abre um caixa para o operador, com o fundo de troco informado. */
    public static CashRegisterSession open(String operator, BigDecimal openingAmount, String warehouseCode) {
        return new CashRegisterSession(null, operator, Instant.now(), openingAmount, warehouseCode,
                null, null, null, null, null, Status.OPEN);
    }

    /** Reconstitui a partir de persistência. */
    public static CashRegisterSession of(Long id, String operator, Instant openedAt,
            BigDecimal openingAmount, String warehouseCode, Instant closedAt, String closedBy,
            BigDecimal expectedAmount, BigDecimal countedAmount, BigDecimal differenceAmount,
            Status status) {
        return new CashRegisterSession(id, operator, openedAt, openingAmount, warehouseCode, closedAt,
                closedBy, expectedAmount, countedAmount, differenceAmount, status);
    }

    /**
     * Fecha o caixa carimbando esperado, contado e a diferença entre os dois.
     *
     * <p><b>Não recusa divergência</b> — registra. Ver a nota sobre o balanço de inventário na
     * documentação do tipo.</p>
     *
     * @throws IllegalStateException se a sessão já estiver fechada. Fechar duas vezes reescreveria a
     *         conferência original, que é justamente o registro que precisa ser imutável.
     */
    public CashRegisterSession closedWith(BigDecimal expected, BigDecimal counted, String closedBy) {
        if (status == Status.CLOSED) {
            throw new IllegalStateException("sessão de caixa " + id + " já está fechada");
        }
        if (expected == null) {
            throw new IllegalArgumentException("expectedAmount é obrigatório no fechamento");
        }
        if (counted == null) {
            throw new IllegalArgumentException("countedAmount é obrigatório no fechamento");
        }
        if (closedBy == null || closedBy.isBlank()) {
            throw new IllegalArgumentException("closedBy é obrigatório no fechamento");
        }
        return new CashRegisterSession(id, operator, openedAt, openingAmount, warehouseCode,
                Instant.now(), closedBy, expected, counted, counted.subtract(expected), Status.CLOSED);
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    /** Indica se o operador é o dono desta sessão — base do 403 {@code SESSION_NOT_OWNED}. */
    public boolean belongsTo(String username) {
        return operator.equals(username);
    }

    /**
     * Indica se o fechamento encontrou diferença entre contado e esperado. Mesma semântica de
     * {@code StockCountItem.diverges()}.
     */
    public boolean diverges() {
        return differenceAmount != null && differenceAmount.signum() != 0;
    }
}
