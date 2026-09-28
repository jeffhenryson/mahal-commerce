package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Perfil da loja impresso no cupom. Campos ausentes vêm nulos. */
@Data
@Schema(description = "Dados da loja impressos no cabeçalho e rodapé do cupom de venda (não fiscal).")
public class StoreProfileResponseDTO {
    private String tradeName;
    private String legalName;
    private String cnpj;
    private String stateRegistration;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zipCode;
    private String phone;
    private String instagram;
    private String website;
    private String logoUrl;
    @Schema(description = "Mensagem livre ao fim do cupom (ex.: agradecimento, política de troca).")
    private String receiptFooter;
}
