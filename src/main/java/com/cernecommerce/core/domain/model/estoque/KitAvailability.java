package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;
import java.util.List;

/**
 * Quantos kits um SKU {@code KIT} pode montar num depósito, derivado do saldo disponível dos
 * componentes (Bloco 1.1 do BACKEND_TODO de mahal-admin) — resolve no servidor o que o frontend
 * hoje deriva com {@code N+1} chamadas (catálogo de kits + catálogo geral + saldos + uma consulta
 * de receita por kit).
 */
public record KitAvailability(String kitSku, String kitName, BigDecimal buildableQuantity, boolean blocked,
        List<KitComponentAvailability> components) {
}
