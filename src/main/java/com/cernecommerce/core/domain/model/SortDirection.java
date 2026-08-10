package com.cernecommerce.core.domain.model;

/**
 * Sentido de ordenação. Existe como tipo do domínio para que {@code core} não precise importar
 * {@code org.springframework.data.domain.Sort.Direction} — o teste de arquitetura proíbe Spring
 * em {@code core.domain}, e a tradução é trivial no adapter.
 */
public enum SortDirection {
    ASC,
    DESC
}
