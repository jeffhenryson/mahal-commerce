package com.cernecommerce.core.ports.out.ratelimit;

/**
 * Rate limit por recurso de negócio (distinto de {@link LoginRateLimiterPort}, que é
 * exclusivo do fluxo de autenticação e usa IP como chave única). Cada bucket tem sua
 * própria janela/limite, e a chave pode ser o usuário autenticado ou o IP, dependendo
 * do endpoint protegido.
 */
public interface ResourceRateLimiterPort {

    /**
     * @throws com.cernecommerce.core.domain.exception.ratelimit.RateLimitExceededException
     *         quando o limite configurado para {@code bucket} já foi consumido por {@code key}
     *         dentro da janela corrente.
     */
    void checkOrThrow(String bucket, String key);
}
