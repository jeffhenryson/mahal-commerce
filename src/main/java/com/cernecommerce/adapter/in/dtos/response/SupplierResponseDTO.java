package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.compras.Supplier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Fornecedor na API (COM-C002).
 *
 * <p>Existe porque {@code GET /compras/suppliers} devolvia {@code PageResult<Supplier>} — o record
 * de <b>domínio</b> vazando direto para o contrato, o único ponto da API onde isso acontecia.
 * Qualquer campo acrescentado ao domínio viraria mudança de contrato sem ninguém decidir.</p>
 */
@Data
@Schema(description = "Fornecedor de mercadorias")
public class SupplierResponseDTO {

    @Schema(description = "Identificador", example = "12")
    private Long id;

    @Schema(description = "Razão social", example = "Distribuidora Zomo LTDA")
    private String legalName;

    @Schema(description = "CNPJ ou CPF, **só dígitos** — é assim que a importação de NF-e casa o "
            + "emitente da nota, que chega do XML sem máscara.", example = "12345678000199")
    private String taxId;

    @Schema(description = "E-mail de contato. Nulo quando não informado.",
            example = "contato@zomo.com.br")
    private String email;

    @Schema(description = "Fornecedor inativo não é oferecido em recebimento novo, mas continua "
            + "resolvendo os recebimentos já registrados.", example = "true")
    private boolean active;

    public static SupplierResponseDTO from(Supplier supplier) {
        SupplierResponseDTO dto = new SupplierResponseDTO();
        dto.setId(supplier.id());
        dto.setLegalName(supplier.legalName());
        dto.setTaxId(supplier.taxId());
        dto.setEmail(supplier.email());
        dto.setActive(supplier.active());
        return dto;
    }
}
