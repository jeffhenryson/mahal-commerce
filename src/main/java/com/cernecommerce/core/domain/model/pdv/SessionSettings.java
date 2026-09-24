package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuração do cardápio de sessão (PDV-F021): qual utensílio é o vaso padrão e qual é o grande,
 * quanto custa o upgrade, e em quais dias da semana o 2º rosh sai de graça ("duplo rosh").
 */
public record SessionSettings(String vasoPadraoCodigo, String vasoGrandeCodigo, BigDecimal upgradeVasoGrandePreco,
        Set<DayOfWeek> diasDuploRosh) {

    public SessionSettings {
        vasoPadraoCodigo = normalizeCode(vasoPadraoCodigo);
        vasoGrandeCodigo = normalizeCode(vasoGrandeCodigo);
        if (upgradeVasoGrandePreco == null || upgradeVasoGrandePreco.signum() < 0) {
            throw new IllegalArgumentException("preço do upgrade de vaso é obrigatório e não pode ser negativo");
        }
        diasDuploRosh = diasDuploRosh == null || diasDuploRosh.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(DayOfWeek.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(diasDuploRosh));
    }

    public static SessionSettings defaults() {
        return new SessionSettings(null, null, BigDecimal.ZERO, Set.of());
    }

    public boolean isDuploRoshDay(DayOfWeek day) {
        return diasDuploRosh.contains(day);
    }

    /** Forma persistida de {@link #diasDuploRosh}: nomes de {@link DayOfWeek} separados por vírgula. */
    public String diasDuploRoshAsText() {
        return diasDuploRosh.stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
    }

    public static Set<DayOfWeek> parseDias(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> DayOfWeek.valueOf(s.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    private static String normalizeCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
