package com.cernecommerce.core.domain.exception.estoque;

public class BarcodeNotFoundException extends RuntimeException {
    public BarcodeNotFoundException(String barcode) {
        super("Código de barras não encontrado no catálogo: " + barcode);
    }
}
