package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.storage.FileServeResult;

/**
 * Upload e leitura das imagens do catálogo de produtos.
 *
 * <p>Deliberadamente <b>desacoplado do produto</b>: o upload não recebe SKU e não escreve nada em
 * {@code product}. O admin sobe a imagem enquanto ainda está preenchendo o formulário, antes de o
 * produto existir, e depois manda a URL devolvida no campo {@code imageUrl}/{@code images} do
 * {@code POST /estoque/products}. Amarrar o upload a um SKU existente obrigaria a criar o produto
 * antes de escolher a foto, que é a ordem inversa da que o operador usa.</p>
 *
 * <p>A contrapartida é que imagem enviada e nunca referenciada fica órfã no storage. É barato e
 * conhecido: não há exclusão automática aqui, pelo mesmo motivo de EST-C011 — apagar em massa o
 * que "parece" não referenciado destrói o que ainda estava sendo cadastrado.</p>
 */
public interface ProductImageUseCase {

    /** Valida formato e tamanho, armazena e devolve a URL pública da imagem. */
    String upload(byte[] bytes);

    /** Resolve como servir o arquivo: redirect para CDN/S3 ou bytes locais. */
    FileServeResult serve(String filename);
}
