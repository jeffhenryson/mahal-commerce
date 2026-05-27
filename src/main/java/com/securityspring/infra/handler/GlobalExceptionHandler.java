package com.securityspring.infra.handler;

import com.securityspring.core.domain.exception.PermissionAlreadyExistsException;
import com.securityspring.core.domain.exception.PermissionNotFoundException;
import com.securityspring.core.domain.exception.RoleAlreadyExistsException;
import com.securityspring.core.domain.exception.auth.AccountLockedException;
import com.securityspring.core.domain.exception.auth.InvalidPasswordException;
import com.securityspring.core.domain.exception.auth.InvalidRefreshTokenException;
import com.securityspring.core.domain.exception.auth.InvalidTotpCodeException;
import com.securityspring.core.domain.exception.auth.PasswordResetTokenExpiredException;
import com.securityspring.core.domain.exception.auth.PasswordResetTokenNotFoundException;
import com.securityspring.core.domain.exception.auth.RefreshTokenAlreadyUsedException;
import com.securityspring.core.domain.exception.auth.TotpAlreadyEnabledException;
import com.securityspring.core.domain.exception.auth.TotpChallengeExpiredException;
import com.securityspring.core.domain.exception.auth.TotpNotEnabledException;
import com.securityspring.core.domain.exception.auth.RefreshTokenExpiredException;
import com.securityspring.core.domain.exception.email.EmailAlreadyVerifiedException;
import com.securityspring.core.domain.exception.email.EmailDeliveryException;
import com.securityspring.core.domain.exception.email.EmailVerificationCodeExpiredException;
import com.securityspring.core.domain.exception.email.EmailVerificationCodeNotFoundException;
import com.securityspring.core.domain.exception.rbac.RoleNotFoundException;
import com.securityspring.core.domain.exception.user.EmailAlreadyExistsException;
import com.securityspring.core.domain.exception.user.UserNotFoundException;
import com.securityspring.core.domain.exception.user.UsernameAlreadyExistsException;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @org.springframework.beans.factory.annotation.Value("${auth.lockout.duration-minutes:15}")
    private long lockoutDurationMinutes;

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "USER_NOT_FOUND", req);
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ApiError> handleRoleNotFound(RoleNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "ROLE_NOT_FOUND", req);
    }

    @ExceptionHandler(PermissionNotFoundException.class)
    public ResponseEntity<ApiError> handlePermissionNotFound(PermissionNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "PERMISSION_NOT_FOUND", req);
    }

    @ExceptionHandler(RoleAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleRoleExists(RoleAlreadyExistsException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "ROLE_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(PermissionAlreadyExistsException.class)
    public ResponseEntity<ApiError> handlePermissionExists(PermissionAlreadyExistsException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "PERMISSION_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleUsernameExists(UsernameAlreadyExistsException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "USERNAME_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailExists(EmailAlreadyExistsException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "EMAIL_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(EmailAlreadyVerifiedException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyVerified(EmailAlreadyVerifiedException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "EMAIL_ALREADY_VERIFIED", req);
    }

    @ExceptionHandler(EmailVerificationCodeNotFoundException.class)
    public ResponseEntity<ApiError> handleVerificationCodeNotFound(EmailVerificationCodeNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "VERIFICATION_CODE_INVALID", req);
    }

    @ExceptionHandler(EmailVerificationCodeExpiredException.class)
    public ResponseEntity<ApiError> handleVerificationCodeExpired(EmailVerificationCodeExpiredException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "VERIFICATION_CODE_EXPIRED", req);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiError> handleAccountLocked(AccountLockedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(lockoutDurationMinutes * 60))
                .body(ApiError.of(ex.getMessage(), "ACCOUNT_LOCKED", req.getRequestURI(), MDC.get("traceId")));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "Credenciais inválidas", "INVALID_CREDENTIALS", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "Credenciais inválidas", "INVALID_CREDENTIALS", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, "Acesso negado", "ACCESS_DENIED", req);
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ApiError> handleInvalidPassword(InvalidPasswordException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_PASSWORD", req);
    }

    @ExceptionHandler(PasswordResetTokenNotFoundException.class)
    public ResponseEntity<ApiError> handlePasswordResetTokenNotFound(PasswordResetTokenNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "PASSWORD_RESET_TOKEN_INVALID", req);
    }

    @ExceptionHandler(PasswordResetTokenExpiredException.class)
    public ResponseEntity<ApiError> handlePasswordResetTokenExpired(PasswordResetTokenExpiredException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "PASSWORD_RESET_TOKEN_EXPIRED", req);
    }

    @ExceptionHandler(InvalidTotpCodeException.class)
    public ResponseEntity<ApiError> handleInvalidTotpCode(InvalidTotpCodeException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_TOTP_CODE", req);
    }

    @ExceptionHandler(TotpChallengeExpiredException.class)
    public ResponseEntity<ApiError> handleTotpChallengeExpired(TotpChallengeExpiredException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), "TOTP_CHALLENGE_EXPIRED", req);
    }

    @ExceptionHandler(TotpAlreadyEnabledException.class)
    public ResponseEntity<ApiError> handleTotpAlreadyEnabled(TotpAlreadyEnabledException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "TOTP_ALREADY_ENABLED", req);
    }

    @ExceptionHandler(TotpNotEnabledException.class)
    public ResponseEntity<ApiError> handleTotpNotEnabled(TotpNotEnabledException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "TOTP_NOT_ENABLED", req);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "Token de atualização inválido", "INVALID_REFRESH_TOKEN", req);
    }

    @ExceptionHandler(RefreshTokenExpiredException.class)
    public ResponseEntity<ApiError> handleRefreshTokenExpired(RefreshTokenExpiredException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "Sessão expirada — faça login novamente", "REFRESH_TOKEN_EXPIRED", req);
    }

    @ExceptionHandler(RefreshTokenAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleRefreshTokenReuse(RefreshTokenAlreadyUsedException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "Sessão inválida — faça login novamente", "REFRESH_TOKEN_REUSED", req);
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ApiError> handleEmailDelivery(EmailDeliveryException ex, HttpServletRequest req) {
        // Conta criada, mas email não entregue. Cliente deve orientar o usuário a usar resend-verification.
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), "EMAIL_DELIVERY_FAILED", req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "Requisição inválida", "BAD_REQUEST", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR", req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido ou ausente", "UNREADABLE_BODY", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado", "INTERNAL_ERROR", req);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, String code, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ApiError.of(message, code, req.getRequestURI(), MDC.get("traceId")));
    }
}
