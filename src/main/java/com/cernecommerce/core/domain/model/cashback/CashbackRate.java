package com.cernecommerce.core.domain.model.cashback;

import com.cernecommerce.core.domain.model.Money;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Taxa de cashback vigente para uma abrangência (CRM-F003, §2.4 do plano de arquitetura).
 *
 * <p>Taxa <b>zero é significativa</b> e diferente de "sem taxa definida": a linha existe com
 * {@code percent = 0}. Por isso abrangência é linha, não coluna anulável em {@code product}.</p>
 *
 * @param scopeRef {@code null} para {@link CashbackScope#GLOBAL}; SKU ou categoria conforme
 *        {@link #scope}.
 */
public record CashbackRate(Long id, CashbackScope scope, String scopeRef, BigDecimal percent,
        boolean active, Instant validFrom, Instant validTo, Instant createdAt) {

    public CashbackRate {
        if (scope == null) {
            throw new IllegalArgumentException("scope é obrigatório");
        }
        boolean hasScopeRef = scopeRef != null && !scopeRef.isBlank();
        if (scope == CashbackScope.GLOBAL && hasScopeRef) {
            throw new IllegalArgumentException("scope GLOBAL não pode ter scopeRef");
        }
        if (scope != CashbackScope.GLOBAL && !hasScopeRef) {
            throw new IllegalArgumentException("scope " + scope + " exige scopeRef");
        }
        if (percent == null || percent.signum() < 0 || percent.compareTo(Money.HUNDRED) > 0) {
            throw new IllegalArgumentException("percent deve estar entre 0 e 100");
        }
        if (validFrom == null) {
            throw new IllegalArgumentException("validFrom é obrigatório");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt é obrigatório");
        }
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException("validTo deve ser posterior a validFrom");
        }
    }

    /** Cria uma taxa GLOBAL, ativa a partir de agora. */
    public static CashbackRate global(BigDecimal percent) {
        Instant now = Instant.now();
        return new CashbackRate(null, CashbackScope.GLOBAL, null, percent, true, now, null, now);
    }

    /** Cria uma taxa por categoria, ativa a partir de agora. */
    public static CashbackRate forCategory(String category, BigDecimal percent) {
        Instant now = Instant.now();
        return new CashbackRate(null, CashbackScope.CATEGORY, category, percent, true, now, null, now);
    }

    /** Cria uma taxa por SKU, ativa a partir de agora. */
    public static CashbackRate forSku(String sku, BigDecimal percent) {
        Instant now = Instant.now();
        return new CashbackRate(null, CashbackScope.SKU, sku, percent, true, now, null, now);
    }

    /** Reconstitui uma taxa a partir de persistência. */
    public static CashbackRate of(Long id, CashbackScope scope, String scopeRef, BigDecimal percent,
            boolean active, Instant validFrom, Instant validTo, Instant createdAt) {
        return new CashbackRate(id, scope, scopeRef, percent, active, validFrom, validTo, createdAt);
    }

    /** Indica se esta taxa vale no instante informado. */
    public boolean appliesAt(Instant moment) {
        if (!active) {
            return false;
        }
        if (moment.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || moment.isBefore(validTo);
    }

    /**
     * Prioridade de resolução na cadeia SKU → CATEGORY → GLOBAL: quanto menor, mais específica.
     */
    public int priority() {
        return switch (scope) {
            case SKU -> 0;
            case CATEGORY -> 1;
            case GLOBAL -> 2;
        };
    }

    /**
     * Altera parcialmente a taxa — {@code null} significa "não mexer neste campo", mesma
     * semântica de {@code Pricing.withPatch}/{@code Product.withDetails}.
     */
    public CashbackRate withPatch(BigDecimal newPercent, Boolean newActive, Instant newValidTo) {
        return new CashbackRate(id, scope, scopeRef,
                newPercent == null ? percent : newPercent,
                newActive == null ? active : newActive,
                validFrom,
                newValidTo == null ? validTo : newValidTo,
                createdAt);
    }
}
