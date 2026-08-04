package com.cernecommerce.core.domain.model.auth;

/**
 * Discrimina operador (backoffice/PDV) de cliente do marketplace dentro da mesma tabela
 * {@code users} (plano-pdv-marketplace.md §2.9). Existe só para permitir, no schema, que um
 * cliente e um operador compartilhem o mesmo username sem violar a constraint composta
 * {@code (user_type, username)} — na prática a aplicação nunca deixa essa colisão acontecer
 * (checagem cruzada em {@code UserService}), então o discriminador é rede de segurança, não o
 * mecanismo principal de unicidade.
 */
public enum UserType {
    OPERATOR,
    CUSTOMER
}
