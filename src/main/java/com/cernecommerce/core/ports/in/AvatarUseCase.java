package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.storage.FileServeResult;

public interface AvatarUseCase {
    /** Valida, armazena e associa o avatar ao usuário. Retorna a URL pública. */
    String upload(String username, byte[] bytes, String originalFilename);

    /** Remove o avatar do usuário. No-op se não tiver avatar. */
    void delete(String username);

    /** Resolve como servir o arquivo: redirect para CDN/S3 ou bytes locais. */
    FileServeResult serve(String filename);
}
