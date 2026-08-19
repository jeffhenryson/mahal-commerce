package com.cernecommerce.infra.handler;

import com.cernecommerce.core.domain.exception.ModuleDisabledException;
import com.cernecommerce.core.domain.exception.auth.TotpSetupRequiredException;
import com.cernecommerce.core.domain.exception.avatar.AvatarTooLargeException;
import com.cernecommerce.core.domain.exception.avatar.InvalidAvatarFormatException;
import com.cernecommerce.core.domain.exception.storage.ImageTooLargeException;
import com.cernecommerce.core.domain.exception.storage.InvalidImageFormatException;
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
import com.cernecommerce.core.domain.exception.ratelimit.RateLimitExceededException;
import com.cernecommerce.core.domain.exception.email.EmailAlreadyVerifiedException;
import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.exception.email.EmailVerificationCodeExpiredException;
import com.cernecommerce.core.domain.exception.email.EmailVerificationCodeNotFoundException;
import com.cernecommerce.core.domain.exception.rbac.RoleNotFoundException;
import com.cernecommerce.core.domain.exception.cashback.CashbackRateAlreadyExistsException;
import com.cernecommerce.core.domain.exception.cashback.CashbackRateNotFoundException;
import com.cernecommerce.core.domain.exception.compras.SupplierNotFoundException;
import com.cernecommerce.core.domain.exception.ecommerce.CartEmptyException;
import com.cernecommerce.core.domain.exception.ecommerce.CartItemNotFoundException;
import com.cernecommerce.core.domain.exception.financeiro.CashFlowEntryNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.BarcodeNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.DefaultWarehouseNotConfiguredException;
import com.cernecommerce.core.domain.exception.estoque.BrandHasProductsException;
import com.cernecommerce.core.domain.exception.estoque.BrandNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.CategoryHasProductsException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateAttributeTypeNameException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateBarcodeException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateBrandNameException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateKitComponentException;
import com.cernecommerce.core.domain.exception.estoque.CategoryNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateCategoryNameException;
import com.cernecommerce.core.domain.exception.estoque.ReplenishmentItemNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.VariantHasStockHistoryException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DraftLimitReachedException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.EmptyKitRecipeException;
import com.cernecommerce.core.domain.exception.estoque.InactiveProductException;
import com.cernecommerce.core.domain.exception.estoque.InactiveWarehouseException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentAlreadyInUseException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentInactiveException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentNotEligibleException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentNotSimpleException;
import com.cernecommerce.core.domain.exception.estoque.KitInitialStockNotAllowedException;
import com.cernecommerce.core.domain.exception.estoque.KitCostNotEditableException;
import com.cernecommerce.core.domain.exception.estoque.KitDirectAdjustmentException;
import com.cernecommerce.core.domain.exception.estoque.KitHasVariantsException;
import com.cernecommerce.core.domain.exception.estoque.KitSelfReferenceException;
import com.cernecommerce.core.domain.exception.estoque.LotExpiryDateMismatchException;
import com.cernecommerce.core.domain.exception.estoque.MissingLotInfoException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ProductVariantNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountAlreadyOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockLotNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotActiveException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.UnexpectedLotInfoException;
import com.cernecommerce.core.domain.exception.estoque.UnexpectedUnitCostException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.exception.crm.AutomationWebhookNotConfiguredException;
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
import com.cernecommerce.core.domain.exception.pagamento.PaymentGatewayException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.exception.compras.MalformedNfeXmlException;
import com.cernecommerce.core.domain.exception.compras.NfeImportAlreadyProcessedException;
import com.cernecommerce.core.domain.exception.compras.NfeImportNotFoundException;
import com.cernecommerce.core.domain.exception.compras.SupplierNotFoundByTaxIdException;
import com.cernecommerce.core.domain.exception.compras.UnmatchedNfeLineException;
import com.cernecommerce.core.domain.exception.pdv.ComandaEmptyException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException;
import com.cernecommerce.core.domain.exception.pdv.NoOpenCashRegisterSessionException;
import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;
import com.cernecommerce.core.domain.exception.pedido.InvalidReportPeriodException;
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

    @ExceptionHandler(DuplicateBarcodeException.class)
    public ResponseEntity<ApiError> handleDuplicateBarcode(DuplicateBarcodeException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "BARCODE_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(BarcodeNotFoundException.class)
    public ResponseEntity<ApiError> handleBarcodeNotFound(BarcodeNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "BARCODE_NOT_FOUND", req);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiError> handleCategoryNotFound(CategoryNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "CATEGORY_NOT_FOUND", req);
    }

    @ExceptionHandler(DuplicateCategoryNameException.class)
    public ResponseEntity<ApiError> handleDuplicateCategoryName(DuplicateCategoryNameException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "CATEGORY_NAME_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(CategoryHasProductsException.class)
    public ResponseEntity<ApiError> handleCategoryHasProducts(CategoryHasProductsException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "CATEGORY_HAS_PRODUCTS", req);
    }

    @ExceptionHandler(BrandNotFoundException.class)
    public ResponseEntity<ApiError> handleBrandNotFound(BrandNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "BRAND_NOT_FOUND", req);
    }

    @ExceptionHandler(DuplicateBrandNameException.class)
    public ResponseEntity<ApiError> handleDuplicateBrandName(DuplicateBrandNameException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "BRAND_NAME_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(BrandHasProductsException.class)
    public ResponseEntity<ApiError> handleBrandHasProducts(BrandHasProductsException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "BRAND_HAS_PRODUCTS", req);
    }

    @ExceptionHandler(DuplicateAttributeTypeNameException.class)
    public ResponseEntity<ApiError> handleDuplicateAttributeTypeName(DuplicateAttributeTypeNameException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "ATTRIBUTE_TYPE_NAME_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(VariantHasStockHistoryException.class)
    public ResponseEntity<ApiError> handleVariantHasStockHistory(VariantHasStockHistoryException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "VARIANT_HAS_STOCK_HISTORY", req);
    }

    @ExceptionHandler(ReplenishmentItemNotFoundException.class)
    public ResponseEntity<ApiError> handleReplenishmentItemNotFound(ReplenishmentItemNotFoundException ex,
            HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "REPLENISHMENT_ITEM_NOT_FOUND", req);
    }

    @ExceptionHandler(DuplicateWarehouseCodeException.class)
    public ResponseEntity<ApiError> handleDuplicateWarehouseCode(DuplicateWarehouseCodeException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "WAREHOUSE_CODE_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(WarehouseNotFoundException.class)
    public ResponseEntity<ApiError> handleWarehouseNotFound(WarehouseNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "WAREHOUSE_NOT_FOUND", req);
    }

    /** ECM-F002: config ausente, não erro do cliente — 503, mesma família de MODULE_DISABLED. */
    @ExceptionHandler(DefaultWarehouseNotConfiguredException.class)
    public ResponseEntity<ApiError> handleDefaultWarehouseNotConfigured(DefaultWarehouseNotConfiguredException ex,
            HttpServletRequest req) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), "DEFAULT_WAREHOUSE_NOT_CONFIGURED", req);
    }

    // ECM-F003 — carrinho do marketplace.
    @ExceptionHandler(CartEmptyException.class)
    public ResponseEntity<ApiError> handleCartEmpty(CartEmptyException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "CART_EMPTY", req);
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ApiError> handleCartItemNotFound(CartItemNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "CART_ITEM_NOT_FOUND", req);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleProductNotFound(ProductNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "PRODUCT_NOT_FOUND", req);
    }

    @ExceptionHandler(ProductVariantNotFoundException.class)
    public ResponseEntity<ApiError> handleProductVariantNotFound(ProductVariantNotFoundException ex,
            HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "PRODUCT_VARIANT_NOT_FOUND", req);
    }

    // EST-F015 — kits virtuais, de um nível só.
    @ExceptionHandler(EmptyKitRecipeException.class)
    public ResponseEntity<ApiError> handleEmptyKitRecipe(EmptyKitRecipeException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "KIT_RECIPE_EMPTY", req);
    }

    @ExceptionHandler(KitHasVariantsException.class)
    public ResponseEntity<ApiError> handleKitHasVariants(KitHasVariantsException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "KIT_HAS_VARIANTS", req);
    }

    @ExceptionHandler(KitComponentAlreadyInUseException.class)
    public ResponseEntity<ApiError> handleKitComponentAlreadyInUse(KitComponentAlreadyInUseException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "KIT_COMPONENT_ALREADY_IN_USE", req);
    }

    @ExceptionHandler(KitSelfReferenceException.class)
    public ResponseEntity<ApiError> handleKitSelfReference(KitSelfReferenceException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "KIT_SELF_REFERENCE", req);
    }

    @ExceptionHandler(DuplicateKitComponentException.class)
    public ResponseEntity<ApiError> handleDuplicateKitComponent(DuplicateKitComponentException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "DUPLICATE_KIT_COMPONENT", req);
    }

    @ExceptionHandler(KitComponentNotSimpleException.class)
    public ResponseEntity<ApiError> handleKitComponentNotSimple(KitComponentNotSimpleException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "KIT_COMPONENT_NOT_SIMPLE", req);
    }

    @ExceptionHandler(KitComponentNotEligibleException.class)
    public ResponseEntity<ApiError> handleKitComponentNotEligible(KitComponentNotEligibleException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "KIT_COMPONENT_NOT_ELIGIBLE", req);
    }

    @ExceptionHandler(KitComponentInactiveException.class)
    public ResponseEntity<ApiError> handleKitComponentInactive(KitComponentInactiveException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "KIT_COMPONENT_INACTIVE", req);
    }

    @ExceptionHandler(KitInitialStockNotAllowedException.class)
    public ResponseEntity<ApiError> handleKitInitialStockNotAllowed(KitInitialStockNotAllowedException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "KIT_INITIAL_STOCK_NOT_ALLOWED", req);
    }

    @ExceptionHandler(DraftLimitReachedException.class)
    public ResponseEntity<ApiError> handleDraftLimitReached(DraftLimitReachedException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "DRAFT_LIMIT_REACHED", req);
    }

    @ExceptionHandler(KitDirectAdjustmentException.class)
    public ResponseEntity<ApiError> handleKitDirectAdjustment(KitDirectAdjustmentException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "KIT_DIRECT_ADJUSTMENT", req);
    }

    @ExceptionHandler(KitCostNotEditableException.class)
    public ResponseEntity<ApiError> handleKitCostNotEditable(KitCostNotEditableException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "KIT_COST_NOT_EDITABLE", req);
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

    // EST-F008 — lote e validade.
    @ExceptionHandler(MissingLotInfoException.class)
    public ResponseEntity<ApiError> handleMissingLotInfo(MissingLotInfoException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "LOT_INFO_REQUIRED", req);
    }

    @ExceptionHandler(UnexpectedLotInfoException.class)
    public ResponseEntity<ApiError> handleUnexpectedLotInfo(UnexpectedLotInfoException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "LOT_INFO_NOT_APPLICABLE", req);
    }

    // EST-F007 — custo médio ponderado.
    @ExceptionHandler(UnexpectedUnitCostException.class)
    public ResponseEntity<ApiError> handleUnexpectedUnitCost(UnexpectedUnitCostException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "UNIT_COST_NOT_APPLICABLE", req);
    }

    @ExceptionHandler(LotExpiryDateMismatchException.class)
    public ResponseEntity<ApiError> handleLotExpiryDateMismatch(LotExpiryDateMismatchException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "LOT_EXPIRY_MISMATCH", req);
    }

    @ExceptionHandler(StockLotNotFoundException.class)
    public ResponseEntity<ApiError> handleStockLotNotFound(StockLotNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "STOCK_LOT_NOT_FOUND", req);
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

    @ExceptionHandler(CashbackRateNotFoundException.class)
    public ResponseEntity<ApiError> handleCashbackRateNotFound(CashbackRateNotFoundException ex,
            HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "CASHBACK_RATE_NOT_FOUND", req);
    }

    /** Requisição bem formada, conflita é com a taxa já ativa na mesma abrangência — 409, não 400. */
    @ExceptionHandler(CashbackRateAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleCashbackRateAlreadyExists(CashbackRateAlreadyExistsException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "CASHBACK_RATE_ALREADY_EXISTS", req);
    }

    @ExceptionHandler(MalformedNfeXmlException.class)
    public ResponseEntity<ApiError> handleMalformedNfeXml(MalformedNfeXmlException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "MALFORMED_NFE_XML", req);
    }

    @ExceptionHandler(SupplierNotFoundByTaxIdException.class)
    public ResponseEntity<ApiError> handleSupplierNotFoundByTaxId(SupplierNotFoundByTaxIdException ex,
            HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "SUPPLIER_NOT_FOUND_BY_TAX_ID", req);
    }

    @ExceptionHandler(NfeImportNotFoundException.class)
    public ResponseEntity<ApiError> handleNfeImportNotFound(NfeImportNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "NFE_IMPORT_NOT_FOUND", req);
    }

    @ExceptionHandler(NfeImportAlreadyProcessedException.class)
    public ResponseEntity<ApiError> handleNfeImportAlreadyProcessed(NfeImportAlreadyProcessedException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "NFE_IMPORT_ALREADY_PROCESSED", req);
    }

    @ExceptionHandler(UnmatchedNfeLineException.class)
    public ResponseEntity<ApiError> handleUnmatchedNfeLine(UnmatchedNfeLineException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "UNMATCHED_NFE_LINE", req);
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

    @ExceptionHandler(AutomationWebhookNotConfiguredException.class)
    public ResponseEntity<ApiError> handleAutomationWebhookNotConfigured(AutomationWebhookNotConfiguredException ex,
            HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "AUTOMATION_WEBHOOK_NOT_CONFIGURED", req);
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

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.retryAfterSeconds()))
                .body(ApiError.of(ex.getMessage(), "RATE_LIMIT_EXCEEDED", req.getRequestURI(), MDC.get("traceId")));
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

    // Contrapartes de imagem de produto. Mesmos status das de avatar (400 nos dois casos): o
    // arquivo é parte do corpo da requisição, então tamanho e formato errados são erro de
    // requisição, não de estado do servidor.
    @ExceptionHandler(ImageTooLargeException.class)
    public ResponseEntity<ApiError> handleImageTooLarge(ImageTooLargeException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "IMAGE_TOO_LARGE", req);
    }

    @ExceptionHandler(InvalidImageFormatException.class)
    public ResponseEntity<ApiError> handleInvalidImageFormat(InvalidImageFormatException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_IMAGE_FORMAT", req);
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

    @ExceptionHandler(ComandaNotFoundException.class)
    public ResponseEntity<ApiError> handleComandaNotFound(ComandaNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "COMANDA_NOT_FOUND", req);
    }

    /** PDV-F009: mesma família de CASH_REGISTER_SESSION_CLOSED — o request está bem formado, o estado da comanda que não permite. */
    @ExceptionHandler(ComandaNotOpenException.class)
    public ResponseEntity<ApiError> handleComandaNotOpen(ComandaNotOpenException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "COMANDA_NOT_OPEN", req);
    }

    @ExceptionHandler(ComandaEmptyException.class)
    public ResponseEntity<ApiError> handleComandaEmpty(ComandaEmptyException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "COMANDA_EMPTY", req);
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

    /**
     * ECM-F004: falha ao falar com o gateway externo. 502 aqui é o default (usado pelo checkout);
     * o webhook mapeia esta mesma exceção para 400 localmente, porque o InfinitePay usa 400 como
     * gatilho de nova tentativa — ver {@code PaymentWebhookController}.
     */
    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ApiError> handlePaymentGatewayError(PaymentGatewayException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_GATEWAY, ex.getMessage(), "PAYMENT_GATEWAY_ERROR", req);
    }

    @ExceptionHandler(InvalidOrderStatusTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidOrderStatusTransition(InvalidOrderStatusTransitionException ex,
            HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "INVALID_STATUS_TRANSITION", req);
    }

    @ExceptionHandler(InvalidReportPeriodException.class)
    public ResponseEntity<ApiError> handleInvalidReportPeriod(InvalidReportPeriodException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_REPORT_PERIOD", req);
    }

    @ExceptionHandler(CashFlowEntryNotFoundException.class)
    public ResponseEntity<ApiError> handleCashFlowEntryNotFound(CashFlowEntryNotFoundException ex,
            HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "CASH_FLOW_ENTRY_NOT_FOUND", req);
    }

    // Nome igual ao de pedido.InvalidReportPeriodException (já importado acima) — qualificado
    // por extenso para evitar colisão, mesmo padrão de NoResourceFoundException logo abaixo.
    @ExceptionHandler(com.cernecommerce.core.domain.exception.financeiro.InvalidReportPeriodException.class)
    public ResponseEntity<ApiError> handleInvalidFinanceiroReportPeriod(
            com.cernecommerce.core.domain.exception.financeiro.InvalidReportPeriodException ex,
            HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_REPORT_PERIOD", req);
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
