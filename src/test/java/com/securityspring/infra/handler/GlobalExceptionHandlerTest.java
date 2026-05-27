package com.securityspring.infra.handler;

import com.securityspring.core.domain.exception.*;
import com.securityspring.core.domain.exception.auth.AccountLockedException;
import com.securityspring.core.domain.exception.auth.InvalidPasswordException;
import com.securityspring.core.domain.exception.auth.InvalidRefreshTokenException;
import com.securityspring.core.domain.exception.auth.RefreshTokenAlreadyUsedException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testa cada mapeamento de exceção do {@link GlobalExceptionHandler} de forma isolada,
 * sem subir contexto Spring — instancia o handler diretamente.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest req;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(handler, "lockoutDurationMinutes", 15L);

        req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/test");
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @Test
    void userNotFound_returns404_withCode() {
        ResponseEntity<ApiError> resp = handler.handleUserNotFound(new UserNotFoundException(1L), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().errorCode()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void roleNotFound_returns404_withCode() {
        ResponseEntity<ApiError> resp = handler.handleRoleNotFound(new RoleNotFoundException("ROLE_X"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().errorCode()).isEqualTo("ROLE_NOT_FOUND");
    }

    @Test
    void permissionNotFound_returns404_withCode() {
        ResponseEntity<ApiError> resp = handler.handlePermissionNotFound(new PermissionNotFoundException("PERM_X"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().errorCode()).isEqualTo("PERMISSION_NOT_FOUND");
    }

    // ── 409 Conflict ─────────────────────────────────────────────────────────

    @Test
    void usernameAlreadyExists_returns409_withCode() {
        ResponseEntity<ApiError> resp = handler.handleUsernameExists(new UsernameAlreadyExistsException("alice"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("USERNAME_ALREADY_EXISTS");
    }

    @Test
    void emailAlreadyExists_returns409_withCode() {
        ResponseEntity<ApiError> resp = handler.handleEmailExists(new EmailAlreadyExistsException("a@b.com"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void emailAlreadyVerified_returns409_withCode() {
        ResponseEntity<ApiError> resp = handler.handleEmailAlreadyVerified(new EmailAlreadyVerifiedException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("EMAIL_ALREADY_VERIFIED");
    }

    @Test
    void roleAlreadyExists_returns409_withCode() {
        ResponseEntity<ApiError> resp = handler.handleRoleExists(new RoleAlreadyExistsException("ROLE_X"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("ROLE_ALREADY_EXISTS");
    }

    // ── 429 Too Many Requests ─────────────────────────────────────────────────

    @Test
    void accountLocked_returns429_withRetryAfterHeader() {
        ResponseEntity<ApiError> resp = handler.handleAccountLocked(new AccountLockedException("bloqueado"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resp.getBody().errorCode()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(resp.getHeaders().getFirst("Retry-After")).isEqualTo("900"); // 15min * 60
    }

    // ── 400 Bad Request ───────────────────────────────────────────────────────

    @Test
    void emailVerificationCodeNotFound_returns400() {
        ResponseEntity<ApiError> resp = handler.handleVerificationCodeNotFound(
                new EmailVerificationCodeNotFoundException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("VERIFICATION_CODE_INVALID");
    }

    @Test
    void emailVerificationCodeExpired_returns400() {
        ResponseEntity<ApiError> resp = handler.handleVerificationCodeExpired(
                new EmailVerificationCodeExpiredException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("VERIFICATION_CODE_EXPIRED");
    }

    @Test
    void invalidPassword_returns400_withCode() {
        ResponseEntity<ApiError> resp = handler.handleInvalidPassword(new InvalidPasswordException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("INVALID_PASSWORD");
    }

    @Test
    void illegalArgument_returns400() {
        ResponseEntity<ApiError> resp = handler.handleIllegalArgument(new IllegalArgumentException("bad"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("BAD_REQUEST");
    }

    // ── 401 Unauthorized ─────────────────────────────────────────────────────

    @Test
    void invalidRefreshToken_returns401_withCode() {
        ResponseEntity<ApiError> resp = handler.handleInvalidRefreshToken(new InvalidRefreshTokenException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().errorCode()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void refreshTokenReused_returns401_withCode() {
        ResponseEntity<ApiError> resp = handler.handleRefreshTokenReuse(new RefreshTokenAlreadyUsedException("alice"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().errorCode()).isEqualTo("REFRESH_TOKEN_REUSED");
    }

    @Test
    void refreshTokenExpired_returns401_withCode() {
        ResponseEntity<ApiError> resp = handler.handleRefreshTokenExpired(new RefreshTokenExpiredException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().errorCode()).isEqualTo("REFRESH_TOKEN_EXPIRED");
    }

    // ── 503 Service Unavailable ───────────────────────────────────────────────

    @Test
    void emailDelivery_returns503_withCode() {
        ResponseEntity<ApiError> resp = handler.handleEmailDelivery(new EmailDeliveryException("falhou"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody().errorCode()).isEqualTo("EMAIL_DELIVERY_FAILED");
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────────

    @Test
    void unexpectedException_returns500_withCode() {
        ResponseEntity<ApiError> resp = handler.handleUnexpected(new RuntimeException("boom"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
    }

    // ── path e timestamp na resposta ─────────────────────────────────────────

    @Test
    void error_response_contains_path_and_timestamp() {
        when(req.getRequestURI()).thenReturn("/api/v1/users/99");
        ResponseEntity<ApiError> resp = handler.handleUserNotFound(new UserNotFoundException(99L), req);
        assertThat(resp.getBody().path()).isEqualTo("/api/v1/users/99");
        assertThat(resp.getBody().timestamp()).isNotNull();
    }
}
