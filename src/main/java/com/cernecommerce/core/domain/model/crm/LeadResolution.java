package com.cernecommerce.core.domain.model.crm;

/**
 * Resultado do find-or-create de lead (PDV-F020): o cliente vinculado e se ele acabou de nascer
 * ({@code created = true}) ou já existia e foi reaproveitado por CPF ou telefone.
 */
public record LeadResolution(Customer customer, boolean created) {

    public LeadResolution {
        if (customer == null) {
            throw new IllegalArgumentException("customer é obrigatório");
        }
    }
}
