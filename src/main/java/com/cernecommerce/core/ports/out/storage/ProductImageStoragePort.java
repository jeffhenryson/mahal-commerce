package com.cernecommerce.core.ports.out.storage;

/**
 * Armazenamento das imagens de produto do catálogo.
 *
 * <p>Marcadora: todo o contrato vem de {@link FileStoragePort}. Existe como tipo próprio para
 * separar este bean do de avatar no wiring manual de {@code CoreBeanConfig}, e porque os dois têm
 * configuração independente — um pode estar em S3 e o outro em disco local.</p>
 */
public interface ProductImageStoragePort extends FileStoragePort {
}
