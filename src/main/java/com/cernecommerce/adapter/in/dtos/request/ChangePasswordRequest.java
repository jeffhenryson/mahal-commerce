package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
    @Pattern(regexp = PasswordPolicy.COMPLEXITY_REGEXP, message = PasswordPolicy.COMPLEXITY_MESSAGE)
    private String newPassword;

    /** Obrigatório quando o usuário tem 2FA ativo. Aceita código TOTP (6 dígitos) ou backup code (8 chars). */
    @Size(min = 6, max = 8)
    private String totpCode;

    /** Se true, revoga todos os refresh tokens e bloqueia JWTs anteriores ao término da troca. */
    private boolean revokeOtherSessions = false;
}
