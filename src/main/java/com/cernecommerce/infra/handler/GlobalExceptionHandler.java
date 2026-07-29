package com.cernecommerce.infra.handler;

import com.cernecommerce.core.domain.exception.ModuleDisabledException;
import com.cernecommerce.core.domain.exception.auth.TotpSetupRequiredException;
import com.cernecommerce.core.domain.exception.avatar.AvatarTooLargeException;
import com.cernecommerce.core.domain.exception.avatar.InvalidAvatarFormatException;
import com.cernecommerce.core.domain.exception.PermissionAlreadyExistsException;
import com.cernecommerce.core.domain.exception.PermissionNotFoundException;
import com.cernecommerce.core.domain.exception.RoleAlreadyExistsException;
import com.cernecommerce.core.domain.exception.auth.AccountDisabledException;
import com.cernecommerce.core.domain.exception.auth.AccountLockedException;
import com.cernecommerce.core.domain.exception.auth.DevChallengeExpiredException;
import com.cernecommerce.core.domain.exception.auth.InvalidPasswordException;
import com.cernecommerce.core.domain.exception.auth.OAuthTokenInvalidException;
import com.cernecommerce.core.domain.exception.auth.InvalidRefreshTokenException;
import com.cernecommerce.core.domain.exception.auth.InvalidTotpCodeException;
import com.cernecommerce.core.domain.exception.auth.PasswordResetTokenExpiredException;
import com.cernecommerce.core.domain.exception.auth.PasswordResetTokenNotFoundException;
import com.cernecommerce.core.domain.exception.auth.RefreshTokenAlreadyUsedException;
import com.cernecommerce.core.domain.exception.auth.TotpAlreadyEnabledException;
import com.cernecommerce.core.domain.exception.auth.TotpChallengeExpiredException;
import com.cernecommerce.core.domain.exception.auth.TotpCodeRequiredException;
import com.cernecommerce.core.domain.exception.auth.TotpNotConsecutiveException;
import com.cernecommerce.core.domain.exception.auth.TotpNotEnabledException;
import com.cernecommerce.core.domain.exception.auth.RefreshTokenExpiredException;
import com.cernecommerce.core.domain.exception.auth.SessionNotFoundException;
import com.cernecommerce.core.domain.exception.email.EmailAlreadyVerifiedException;
import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.exception.email.EmailVerificationCodeExpiredException;
import com.cernecommerce.core.domain.exception.email.EmailVerificationCodeNotFoundException;
import com.cernecommerce.core.domain.exception.rbac.RoleNotFoundException;
import com.cernecommerce.core.domain.exception.compras.SupplierNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.InactiveProductException;
import com.cernecommerce.core.domain.exception.estoque.InactiveWarehouseException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountAlreadyOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotActiveException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.exception.crm.DuplicateCustomerCpfException;
import com.cernecommerce.core.domain.exception.crm.DuplicateCustomerEmailException;
import com.cernecommerce.core.domain.exception.crm.DuplicateTagNameException;
import com.cernecommerce.core.domain.exception.crm.TagNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionAlreadyOpenException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pagamento.InsufficientPaymentException;
import com.cernecommerce.core.domain.exception.pagamento.PaymentExceedsOrderTotalException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.exception.pdv.NoOpenCashRegisterSessionException;
import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.user.EmailAlreadyExistsException;
import com.cernecommerce.core.domain.exception.user.UserNotFoundException;
import com.cernecommerce.core.domain.exception.user.UsernameAlreadyExistsException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @org.springframework.beans.factory.annotation.Value("${auth.lockout.duration-minutes:15}")
    private long lockoutDurationMinutes;

    @Autowired(required = false)
    private ApplicationEventPublisher publisher;

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

    @ExceptionHandler(DuplicateSkuException.class)
    public ResponseEntity<ApiError> handleDuplicateSku(DuplicateSkuException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "SKU_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(DuplicateWarehouseCodeException.class)
    public ResponseEntity<ApiError> handleDuplicateWarehouseCode(DuplicateWarehouseCodeException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "WAREHOUSE_CODE_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(WarehouseNotFoundException.class)
    public ResponseEntity<ApiError> handleWarehouseNotFound(WarehouseNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "WAREHOUSE_NOT_FOUND", req);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleProductNotFound(ProductNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "PRODUCT_NOT_FOUND", req);
    }

    /**
     * EST-F018: 409 e não 400 — a requisição está bem formada, o que conflita é o estado do
     * recurso. Mesma família de {@code DuplicateSkuException} e {@code STOCK_UPDATE_CONFLICT}.
     */
    @ExceptionHandler(InactiveProductException.class)
    public ResponseEntity<ApiError> handleInactiveProduct(InactiveProductException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "PRODUCT_INACTIVE", req);
    }

    @ExceptionHandler(InactiveWarehouseException.class)
    public ResponseEntity<ApiError> handleInactiveWarehouse(InactiveWarehouseException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "WAREHOUSE_INACTIVE", req);
    }

    @ExceptionHandler(StockCountAlreadyOpenException.class)
    public ResponseEntity<ApiError> handleStockCountAlreadyOpen(StockCountAlreadyOpenException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "STOCK_COUNT_ALREADY_OPEN", req);
    }

    @ExceptionHandler(StockCountNotFoundException.class)
    public ResponseEntity<ApiError> handleStockCountNotFound(StockCountNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "STOCK_COUNT_NOT_FOUND", req);
    }

    /** Estado do balanço conflita com a operação — fechar duas vezes aplicaria o ajuste em dobro. */
    @ExceptionHandler(StockCountNotOpenException.class)
    public ResponseEntity<ApiError> handleStockCountNotOpen(StockCountNotOpenException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "STOCK_COUNT_NOT_OPEN", req);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "INSUFFICIENT_STOCK", req);
    }

    @ExceptionHandler(StockReservationNotFoundException.class)
    public ResponseEntity<ApiError> handleStockReservationNotFound(StockReservationNotFoundException ex,
            HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "RESERVATION_NOT_FOUND", req);
    }

    /** Reserva já resolvida (consumida, liberada ou expirada) não pode ser consumida/liberada de novo. */
    @ExceptionHandler(StockReservationNotActiveException.class)
    public ResponseEntity<ApiError> handleStockReservationNotActive(StockReservationNotActiveException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "RESERVATION_NOT_ACTIVE", req);
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    public ResponseEntity<ApiError> handleSupplierNotFound(SupplierNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "SUPPLIER_NOT_FOUND", req);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "Conflito de concorrência ao atualizar o saldo de estoque, tente novamente",
                "STOCK_UPDATE_CONFLICT", req);
    }

    @ExceptionHandler(DuplicateCustomerEmailException.class)
    public ResponseEntity<ApiError> handleDuplicateCustomerEmail(DuplicateCustomerEmailException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "CUSTOMER_EMAIL_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(DuplicateCustomerCpfException.class)
    public ResponseEntity<ApiError> handleDuplicateCustomerCpf(DuplicateCustomerCpfException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "CUSTOMER_CPF_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiError> handleCustomerNotFound(CustomerNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "CUSTOMER_NOT_FOUND", req);
    }

    @ExceptionHandler(DuplicateTagNameException.class)
    public ResponseEntity<ApiError> handleDuplicateTagName(DuplicateTagNameException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "TAG_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(TagNotFoundException.class)
    public ResponseEntity<ApiError> handleTagNotFound(TagNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "TAG_NOT_FOUND", req);
    }

    @ExceptionHandler(CampaignAutomationNotFoundException.class)
    public ResponseEntity<ApiError> handleCampaignAutomationNotFound(CampaignAutomationNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "CAMPAIGN_AUTOMATION_NOT_FOUND", req);
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

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiError> handleAccountDisabled(AccountDisabledException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "Credenciais inválidas", "INVALID_CREDENTIALS", req);
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
        if (publisher != null) {
            publisher.publishEvent(AuditEvent.of(
                    AuditEvent.EventType.ACCESS_DENIED,
                    resolveUsername(),
                    Map.of("path", req.getRequestURI())
            ));
        }
        return error(HttpStatus.FORBIDDEN, "Acesso negado", "ACCESS_DENIED", req);
    }

    private static String resolveUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
        } catch (Exception ignored) {
            return "anonymous";
        }
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

    @ExceptionHandler(TotpSetupRequiredException.class)
    public ResponseEntity<ApiError> handleTotpSetupRequired(TotpSetupRequiredException ex, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), "TOTP_SETUP_REQUIRED", req);
    }

    @ExceptionHandler(TotpNotEnabledException.class)
    public ResponseEntity<ApiError> handleTotpNotEnabled(TotpNotEnabledException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "TOTP_NOT_ENABLED", req);
    }

    @ExceptionHandler(ModuleDisabledException.class)
    public ResponseEntity<ApiError> handleModuleDisabled(ModuleDisabledException ex, HttpServletRequest req) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), "MODULE_DISABLED", req);
    }

    @ExceptionHandler(TotpCodeRequiredException.class)
    public ResponseEntity<ApiError> handleTotpCodeRequired(TotpCodeRequiredException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "TOTP_CODE_REQUIRED", req);
    }

    @ExceptionHandler(TotpNotConsecutiveException.class)
    public ResponseEntity<ApiError> handleTotpNotConsecutive(TotpNotConsecutiveException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "TOTP_NOT_CONSECUTIVE", req);
    }

    @ExceptionHandler(DevChallengeExpiredException.class)
    public ResponseEntity<ApiError> handleDevChallengeExpired(DevChallengeExpiredException ex, HttpServletRequest req) {
        return error(HttpStatus.GONE, ex.getMessage(), "DEV_CHALLENGE_EXPIRED", req);
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ApiError> handleSessionNotFound(SessionNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "SESSION_NOT_FOUND", req);
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

    @ExceptionHandler(OAuthTokenInvalidException.class)
    public ResponseEntity<ApiError> handleOAuthTokenInvalid(OAuthTokenInvalidException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), "OAUTH_TOKEN_INVALID", req);
    }

    @ExceptionHandler(AvatarTooLargeException.class)
    public ResponseEntity<ApiError> handleAvatarTooLarge(AvatarTooLargeException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "AVATAR_TOO_LARGE", req);
    }

    @ExceptionHandler(InvalidAvatarFormatException.class)
    public ResponseEntity<ApiError> handleInvalidAvatarFormat(InvalidAvatarFormatException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_AVATAR_FORMAT", req);
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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        String message = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR", req);
    }

    /**
     * Constraint violada em {@code @RequestParam}/{@code @PathVariable} de controller anotado com
     * {@code @Validated} (EST-C005).
     *
     * <p>Não é o mesmo caminho do {@link ConstraintViolationException} acima: desde o Spring
     * Framework 6.1 a validação de parâmetros de handler é <b>nativa</b> do
     * {@code RequestMappingHandlerAdapter}, sem proxy AOP, e lança
     * {@link HandlerMethodValidationException}. Sem este handler ela cairia no catch-all de
     * {@link Exception} e viraria 500 — que era o comportamento real de
     * {@code GET /compras/suppliers?size=200}, cujos {@code @Min}/{@code @Max} nunca tinham sido
     * exercitados por teste.</p>
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleHandlerMethodValidation(HandlerMethodValidationException ex,
            HttpServletRequest req) {
        String message = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(err -> parameterName(result) + ": " + err.getDefaultMessage()))
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST,
                message.isBlank() ? "Parâmetro de requisição inválido" : message, "VALIDATION_ERROR", req);
    }

    /** {@code getParameterName()} depende do flag {@code -parameters} do compilador. */
    private String parameterName(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        return name == null ? "parâmetro" : name;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido ou ausente", "UNREADABLE_BODY", req);
    }

    /**
     * Sem este handler, um {@code @RequestParam} obrigatório ausente cairia no catch-all de
     * {@link Exception} e devolveria 500 em vez de 400 — vale para todo endpoint com parâmetro
     * de query obrigatório, incluindo {@code GET /estoque/stock-balance} e
     * {@code GET /estoque/movements}.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
            HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: '" + ex.getParameterName() + "'",
                "MISSING_PARAMETER", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String message = "Valor inválido para o parâmetro '" + ex.getName() + "'";
        if (ex.getRequiredType() != null && ex.getRequiredType().isEnum()) {
            String valid = Arrays.stream(ex.getRequiredType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            message += ". Valores aceitos: " + valid;
        }
        return error(HttpStatus.BAD_REQUEST, message, "INVALID_ENUM_VALUE", req);
    }

    @ExceptionHandler(CashRegisterSessionNotFoundException.class)
    public ResponseEntity<ApiError> handleCashRegisterSessionNotFound(CashRegisterSessionNotFoundException ex,
            HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "CASH_REGISTER_SESSION_NOT_FOUND", req);
    }

    @ExceptionHandler(CashRegisterSessionClosedException.class)
    public ResponseEntity<ApiError> handleCashRegisterSessionClosed(CashRegisterSessionClosedException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "CASH_REGISTER_SESSION_CLOSED", req);
    }

    @ExceptionHandler(CashRegisterSessionAlreadyOpenException.class)
    public ResponseEntity<ApiError> handleSessionAlreadyOpen(CashRegisterSessionAlreadyOpenException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "SESSION_ALREADY_OPEN", req);
    }

    @ExceptionHandler(NoOpenCashRegisterSessionException.class)
    public ResponseEntity<ApiError> handleNoOpenSession(NoOpenCashRegisterSessionException ex,
            HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "NO_OPEN_SESSION", req);
    }

    /**
     * PDV-C004: 403 e não 404. A sessão existe e o chamador sabe disso — o que falta é ela ser dele.
     * Um 404 aqui esconderia a causa real e faria o operador procurar um caixa que está lá.
     */
    @ExceptionHandler(CashRegisterSessionNotOwnedException.class)
    public ResponseEntity<ApiError> handleSessionNotOwned(CashRegisterSessionNotOwnedException ex,
            HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), "SESSION_NOT_OWNED", req);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "ORDER_NOT_FOUND", req);
    }

    /**
     * PDV-F004: 409 e não 400 — a requisição está bem formada, o que impede a venda é o estado do
     * cadastro. Mesma família de {@code PRODUCT_INACTIVE}.
     */
    @ExceptionHandler(ProductNotPricedException.class)
    public ResponseEntity<ApiError> handleProductNotPriced(ProductNotPricedException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "PRODUCT_NOT_PRICED", req);
    }

    @ExceptionHandler(DiscountLimitExceededException.class)
    public ResponseEntity<ApiError> handleDiscountLimit(DiscountLimitExceededException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "DISCOUNT_LIMIT_EXCEEDED", req);
    }

    /** PDV-F006: mesma família de INSUFFICIENT_STOCK — o request está bem formado, faltou dinheiro. */
    @ExceptionHandler(InsufficientPaymentException.class)
    public ResponseEntity<ApiError> handleInsufficientPayment(InsufficientPaymentException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "INSUFFICIENT_PAYMENT", req);
    }

    /** PDV-F006: mesma família de DISCOUNT_LIMIT_EXCEEDED — conflita com uma regra de negócio, não é malformado. */
    @ExceptionHandler(PaymentExceedsOrderTotalException.class)
    public ResponseEntity<ApiError> handlePaymentExceedsOrderTotal(PaymentExceedsOrderTotalException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "PAYMENT_EXCEEDS_ORDER_TOTAL", req);
    }

    @ExceptionHandler(InvalidOrderStatusTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidOrderStatusTransition(InvalidOrderStatusTransitionException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "INVALID_STATUS_TRANSITION", req);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "Recurso não encontrado", "NOT_FOUND", req);
    }

    /**
     * Rede de segurança para violação de constraint que escape das checagens de aplicação —
     * tipicamente uma corrida entre duas requisições que passaram na mesma validação antes de
     * qualquer uma gravar (ex.: a primeira movimentação simultânea do mesmo par SKU/depósito,
     * que colide em {@code uk_stock_balance_sku_warehouse}). É conflito de concorrência, não erro
     * do servidor, então responde 409. A mensagem é genérica de propósito: o texto do driver
     * expõe nome de tabela, de constraint e valores da linha.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex,
            HttpServletRequest req) {
        log.warn("Violação de integridade em {}", req.getRequestURI(), ex);
        return error(HttpStatus.CONFLICT, "A operação conflita com um registro já existente, tente novamente",
                "DATA_INTEGRITY_VIOLATION", req);
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
