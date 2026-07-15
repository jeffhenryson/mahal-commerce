package com.cernecommerce.adapter.in.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DevFirstCodeResponseDTO {
    private final String devToken;
    private final int expiresIn;  // segundos até expirar (90)
}
