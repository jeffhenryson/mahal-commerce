package com.cernecommerce.core.domain.exception.compras;

import com.cernecommerce.core.domain.model.compras.NfeImportStatus;

/** Tentativa de confirmar um import de NF-e que já foi confirmado ou rejeitado. */
public class NfeImportAlreadyProcessedException extends RuntimeException {
    public NfeImportAlreadyProcessedException(Long id, NfeImportStatus status) {
        super("Import de NF-e " + id + " não está aguardando confirmação: " + status);
    }
}
