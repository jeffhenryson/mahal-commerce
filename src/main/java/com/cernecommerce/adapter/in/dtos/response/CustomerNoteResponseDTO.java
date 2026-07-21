package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.time.Instant;

@Data
public class CustomerNoteResponseDTO {
    private Long id;
    private Long customerId;
    private String autor;
    private String texto;
    private Instant criadoEm;
}
