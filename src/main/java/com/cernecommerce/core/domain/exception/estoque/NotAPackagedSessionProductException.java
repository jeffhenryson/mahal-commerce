package com.cernecommerce.core.domain.exception.estoque;

/**
 * O SKU não é consumido por lata (EST-F027): ou não é produto de sessão, ou não declara
 * {@code sessionsPerUnit}.
 *
 * <p>Existe para separar "não achei a lata" de "este produto não tem lata", que são coisas
 * diferentes para quem chama: a primeira o operador resolve abrindo uma, a segunda é cadastro
 * faltando — {@code sessionsPerUnit} em branco no produto.</p>
 */
public class NotAPackagedSessionProductException extends RuntimeException {
    public NotAPackagedSessionProductException(String sku) {
        super("SKU " + sku + " não é consumido por lata: marque-o como produto de sessão e informe "
                + "sessionsPerUnit no cadastro");
    }
}
