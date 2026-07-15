package com.cernecommerce.adapter.in.controller;

import org.springframework.security.core.Authentication;

final class AuthUtils {

    private AuthUtils() {}

    /**
     * Retorna true se o caller tem ROLE_DEV ou DEV_ELEVATED.
     * Null-safe: retorna false se {@code auth} for null (ex: contexto de teste sem segurança).
     */
    static boolean isDevAccess(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DEV".equals(a.getAuthority())
                        || "DEV_ELEVATED".equals(a.getAuthority()));
    }
}
