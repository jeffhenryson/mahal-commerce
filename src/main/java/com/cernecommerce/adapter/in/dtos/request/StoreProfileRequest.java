package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Substitui o perfil inteiro — campo nulo ou em branco apaga o valor. */
@Data
public class StoreProfileRequest {
    @Size(max = 300) private String tradeName;
    @Size(max = 300) private String legalName;
    @Size(max = 300) private String cnpj;
    @Size(max = 300) private String stateRegistration;
    @Size(max = 300) private String addressLine1;
    @Size(max = 300) private String addressLine2;
    @Size(max = 300) private String city;
    @Size(max = 300) private String state;
    @Size(max = 300) private String zipCode;
    @Size(max = 300) private String phone;
    @Size(max = 300) private String instagram;
    @Size(max = 300) private String website;
    @Size(max = 300) private String logoUrl;
    @Size(max = 300) private String receiptFooter;
}
