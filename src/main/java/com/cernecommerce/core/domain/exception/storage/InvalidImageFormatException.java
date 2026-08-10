package com.cernecommerce.core.domain.exception.storage;

public class InvalidImageFormatException extends RuntimeException {
    public InvalidImageFormatException() {
        super("Formato de imagem inválido. Aceito: JPEG, PNG, WebP");
    }
}
