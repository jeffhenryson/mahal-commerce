package com.securityspring.core.ports.in;

public interface AvatarUseCase {
    /** Valida, armazena e associa o avatar ao usuário. Retorna a URL pública. */
    String upload(String username, byte[] bytes, String originalFilename);

    /** Remove o avatar do usuário. No-op se não tiver avatar. */
    void delete(String username);
}
