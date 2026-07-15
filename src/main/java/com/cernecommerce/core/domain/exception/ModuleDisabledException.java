package com.cernecommerce.core.domain.exception;

public class ModuleDisabledException extends RuntimeException {
    public ModuleDisabledException(String moduleName) {
        super("Módulo '" + moduleName + "' está desabilitado");
    }
}
