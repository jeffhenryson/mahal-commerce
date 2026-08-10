package com.cernecommerce.core.ports.out.storage;

/**
 * Armazenamento dos avatares de usuário.
 *
 * <p>Marcadora: todo o contrato vem de {@link FileStoragePort}. Existe como tipo próprio para
 * separar este bean do de imagem de produto no wiring manual de {@code CoreBeanConfig}.</p>
 */
public interface AvatarStoragePort extends FileStoragePort {
}
