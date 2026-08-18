package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.exception.compras.MalformedNfeXmlException;
import com.cernecommerce.core.domain.model.compras.NfeImportPreview;

/**
 * Extrai de um XML de NF-e os campos que a importação de entrada de mercadoria precisa (EST-F005):
 * CNPJ do emitente e, por item, código do fornecedor, EAN, descrição, quantidade, preço unitário
 * e lote/validade quando declarados. <b>Não</b> valida o XML contra o schema completo da SEFAZ —
 * só os elementos usados.
 */
public interface NfeXmlImportPort {

    /**
     * @throws MalformedNfeXmlException se o XML não for bem formado, não tiver a estrutura mínima
     *         esperada (emitente, ao menos um item), ou for rejeitado pelo hardening contra XXE
     *         (DOCTYPE/entidade externa)
     */
    NfeImportPreview parse(byte[] xmlBytes);
}
