package com.cernecommerce.core.domain.model.pdv;

/**
 * Ciclo de vida de uma {@link Comanda} (PDV-F009): {@code ABERTA} enquanto recebe itens
 * incrementais, {@code FECHADA} quando vira um {@code Order} de verdade, {@code CANCELADA} quando
 * abandonada sem nunca ter sido cobrada.
 */
public enum ComandaStatus {
    ABERTA, FECHADA, CANCELADA
}
