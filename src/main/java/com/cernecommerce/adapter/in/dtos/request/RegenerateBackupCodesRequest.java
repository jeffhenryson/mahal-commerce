package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;

public class RegenerateBackupCodesRequest {

    @NotBlank(message = "Senha atual é obrigatória")
    private String currentPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
}
