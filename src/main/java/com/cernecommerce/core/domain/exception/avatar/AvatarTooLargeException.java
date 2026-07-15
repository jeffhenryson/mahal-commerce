package com.cernecommerce.core.domain.exception.avatar;

public class AvatarTooLargeException extends RuntimeException {
    public AvatarTooLargeException(long maxBytes) {
        super("Avatar excede o tamanho máximo permitido de " + (maxBytes / 1024 / 1024) + " MB");
    }
}
