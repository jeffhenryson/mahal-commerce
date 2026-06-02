package com.securityspring.adapter.in.dtos.response;

public record TotpStatusResponseDTO(boolean enabled, int backupCodesRemaining) {}
