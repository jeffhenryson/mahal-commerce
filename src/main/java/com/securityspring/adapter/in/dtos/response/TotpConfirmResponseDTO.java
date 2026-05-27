package com.securityspring.adapter.in.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TotpConfirmResponseDTO {
    private List<String> backupCodes;
}
