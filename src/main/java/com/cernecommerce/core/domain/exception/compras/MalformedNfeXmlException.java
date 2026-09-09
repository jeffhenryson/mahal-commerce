package com.cernecommerce.core.domain.exception.compras;

/**
 * XML de NF-e malformado, ou rejeitado pelo hardening contra XXE (DOCTYPE/entidade externa).
 *
 * <p><b>EST-C021.</b> A mensagem já se pretendia genérica — para não confirmar a um atacante que a
 * tentativa de XXE foi reconhecida como tal —, mas concatenava {@code e.getMessage()} do
 * {@code SAXException}, e o texto do Xerces confirma exatamente isso ({@code "DOCTYPE is disallowed
 * when the feature ... set to true"}). Além do problema de segurança, o operador recebia jargão de
 * parser Java em inglês dentro de uma frase em português, numa tela que exibe o que vem do
 * servidor. Agora a falha do parser usa {@link #fromParser(Throwable)}: o usuário lê uma frase
 * acionável, e o detalhe técnico vive na causa, alcançável pelo {@code traceId} do log.</p>
 */
public class MalformedNfeXmlException extends RuntimeException {

    private static final String GENERIC =
            "O arquivo não é um XML de NF-e válido ou está incompleto";

    /**
     * Falha estrutural detectada pelo próprio backend, com detalhe redigido aqui — "NF-e sem
     * nenhum item", "arquivo vazio", "&lt;xProd&gt; ausente ou vazio". Esses textos são nossos, em
     * português, e dizem ao operador o que consertar na nota; continuam no corpo da resposta.
     */
    public MalformedNfeXmlException(String detail) {
        super(detail == null || detail.isBlank() ? GENERIC : "XML de NF-e inválido: " + detail);
    }

    private MalformedNfeXmlException(Throwable cause) {
        super(GENERIC, cause);
    }

    /**
     * Falha vinda do parser (XML quebrado, DOCTYPE bloqueado, número/data que não converte) — a
     * mensagem original nunca chega ao cliente.
     */
    public static MalformedNfeXmlException fromParser(Throwable cause) {
        return new MalformedNfeXmlException(cause);
    }
}
