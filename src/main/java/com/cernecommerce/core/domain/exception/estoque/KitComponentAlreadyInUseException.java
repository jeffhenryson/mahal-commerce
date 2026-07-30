package com.cernecommerce.core.domain.exception.estoque;

/**
 * Sustenta a invariante de um nível só (EST-F015, §2.10) contra a porta dos fundos: sem esta
 * checagem, promover a KIT um SKU que já é componente de outro kit criaria kit-dentro-de-kit sem
 * nunca passar pela validação "componente precisa ser SIMPLES", que só roda no momento de definir
 * a receita do lado de fora.
 */
public class KitComponentAlreadyInUseException extends RuntimeException {
    public KitComponentAlreadyInUseException(String sku) {
        super("SKU já é componente de outro kit, não pode virar kit: " + sku);
    }
}
