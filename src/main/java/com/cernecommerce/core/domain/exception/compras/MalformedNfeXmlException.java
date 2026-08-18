package com.cernecommerce.core.domain.exception.compras;

/**
 * XML de NF-e malformado, ou rejeitado pelo hardening contra XXE (DOCTYPE/entidade externa) —
 * nesse caso a mensagem não distingue os dois casos de propósito, para não confirmar a um
 * atacante que a tentativa de XXE foi reconhecida como tal.
 */
public class MalformedNfeXmlException extends RuntimeException {
    public MalformedNfeXmlException(String detail) {
        super("XML de NF-e inválido" + (detail == null ? "" : ": " + detail));
    }
}
