package com.cernecommerce.core.domain.exception.storage;

public class ImageTooLargeException extends RuntimeException {
    public ImageTooLargeException(long maxBytes) {
        super("Imagem excede o tamanho máximo permitido de " + (maxBytes / 1024 / 1024) + " MB");
    }
}
