package com.cernecommerce.core.domain.exception.estoque;

public class DuplicateBarcodeException extends RuntimeException {
    public DuplicateBarcodeException(String barcode) {
        super("Código de barras já cadastrado: " + barcode);
    }
}
