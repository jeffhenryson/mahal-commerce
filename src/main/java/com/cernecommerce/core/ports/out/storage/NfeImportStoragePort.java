package com.cernecommerce.core.ports.out.storage;

/**
 * Armazenamento do XML bruto das NF-e importadas (EST-F005) — trilha de auditoria/disputa com
 * fornecedor. Marcadora: todo o contrato vem de {@link FileStoragePort}. <b>Sem endpoint de
 * leitura pública</b>, diferente de {@link ProductImageStoragePort}/{@link AvatarStoragePort} — o
 * XML não é um ativo de vitrine.
 */
public interface NfeImportStoragePort extends FileStoragePort {
}
