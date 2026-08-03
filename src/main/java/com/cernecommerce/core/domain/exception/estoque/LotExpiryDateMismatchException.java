package com.cernecommerce.core.domain.exception.estoque;

import java.time.LocalDate;

/**
 * O mesmo {@code lotCode} de um SKU já está gravado com outra validade. Um reabastecimento do
 * mesmo lote informando validade diferente é erro de digitação, não atualização — o lote não
 * muda de validade depois de criado.
 */
public class LotExpiryDateMismatchException extends RuntimeException {
    public LotExpiryDateMismatchException(String sku, String lotCode, LocalDate recordedExpiryDate,
            LocalDate informedExpiryDate) {
        super("Lote " + lotCode + " do SKU " + sku + " já está gravado com validade " + recordedExpiryDate
                + "; validade informada agora (" + informedExpiryDate + ") diverge");
    }
}
