package com.securityspring.core.domain.exception.avatar;

public class InvalidAvatarFormatException extends RuntimeException {
    public InvalidAvatarFormatException() {
        super("Formato de imagem inválido. Aceito: JPEG, PNG, WebP");
    }
}
