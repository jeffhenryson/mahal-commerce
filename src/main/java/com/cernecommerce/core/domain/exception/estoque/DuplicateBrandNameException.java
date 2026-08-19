package com.cernecommerce.core.domain.exception.estoque;

public class DuplicateBrandNameException extends RuntimeException {
    public DuplicateBrandNameException(String name) {
        super("Já existe uma marca com o nome: " + name);
    }
}
