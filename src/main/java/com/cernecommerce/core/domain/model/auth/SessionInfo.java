package com.cernecommerce.core.domain.model.auth;

import java.time.Instant;

/**
 * Representa uma sessão ativa do usuário (refresh token não expirado e não
 * revogado).
 *
 * @param id        identificador da sessão (id do refresh token)
 * @param createdAt quando a sessão foi iniciada (login)
 * @param expiresAt quando a sessão expira automaticamente
 * @param ipAddress IP do cliente no momento do login (pode ser null para
 *                  sessões antigas)
 * @param userAgent User-Agent do cliente no momento do login (pode ser null
 *                  para sessões antigas)
 */
public record SessionInfo(Long id, Instant createdAt, Instant expiresAt, String ipAddress, String userAgent) {
}
