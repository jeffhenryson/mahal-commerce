package com.cernecommerce.core.domain.exception.pdv;

/**
 * {@code notes} acima do limite da coluna (PDV-F011).
 *
 * <p>Recusar em vez de truncar foi escolha explícita do contrato: truncado, o operador não fica
 * sabendo que perdeu parte do registro — e o registro é justamente onde mora "qual pinça saiu com
 * aquela mesa", a informação que se vai cobrar de volta se a pinça sumir.</p>
 */
public class NotesTooLongException extends RuntimeException {
    public NotesTooLongException(int length, int max) {
        super("notes tem " + length + " caracteres e o limite é " + max);
    }
}
