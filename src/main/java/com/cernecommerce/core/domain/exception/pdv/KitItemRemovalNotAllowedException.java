package com.cernecommerce.core.domain.exception.pdv;

/**
 * Remoção avulsa de uma linha que pertence a um kit montável (PDV-F019). 409: o desconto do kit foi
 * rateado entre as linhas do pacote, e tirar só uma deixaria as outras com um desconto que o kit
 * incompleto não daria. O pacote sai inteiro, por {@code DELETE /pdv/comandas/{id}/kits/{bundleId}}.
 */
public class KitItemRemovalNotAllowedException extends RuntimeException {

    public KitItemRemovalNotAllowedException(Long itemId, String kitBundleId) {
        super("A linha " + itemId + " faz parte do kit " + kitBundleId + ": remova o kit inteiro");
    }
}
