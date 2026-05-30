package com.securityspring.adapter.in.dtos.request;

import com.securityspring.core.domain.PasswordPolicy;
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
}
