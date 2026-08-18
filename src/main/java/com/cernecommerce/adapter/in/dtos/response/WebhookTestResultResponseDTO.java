package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.util.Map;

@Data
public class WebhookTestResultResponseDTO {
    private boolean success;
    private Integer statusCode;
    private String errorMessage;
    private Map<String, Object> payloadEnviado;
}
