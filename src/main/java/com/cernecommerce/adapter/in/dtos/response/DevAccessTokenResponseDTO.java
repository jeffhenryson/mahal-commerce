package com.cernecommerce.adapter.in.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DevAccessTokenResponseDTO {
    private final String accessToken;
    private final String tokenType = "Bearer";
    private final long expiresIn;
}
