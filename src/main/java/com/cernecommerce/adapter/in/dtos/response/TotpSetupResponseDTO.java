package com.cernecommerce.adapter.in.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TotpSetupResponseDTO {
    private String secret;
    private String otpauthUri;
}
