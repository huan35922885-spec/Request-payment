package tw.com.jsgcpa.paymentapproval.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import tw.com.jsgcpa.paymentapproval.common.api.ApiErrorResponse;
import tw.com.jsgcpa.paymentapproval.common.api.FieldValidationError;
import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentStorageException;
import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentValidationException;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentBusinessException;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentNotFoundException;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentDeleteException;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.master.admin.exception.ExpenseTypeAdminBusinessException;
import tw.com.jsgcpa.paymentapproval.master.admin.exception.ExpensePriceSettingAdminBusinessException;
import tw.com.jsgcpa.paymentapproval.security.exception.AuthenticationBusinessException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldValidationError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new FieldValidationError(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ))
                .sorted(Comparator.comparing(FieldValidationError::field))
                .toList();

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                "Request body is missing or invalid",
                request,
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleQueryParameterTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "attachmentType".equals(exception.getName())
                        ? "PAYMENT_REQUEST_ATTACHMENT_TYPE_INVALID"
                        : "INVALID_QUERY_PARAMETER",
                "Query parameter is invalid",
                request,
                List.of()
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingQueryParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_QUERY_PARAMETER",
                "Query parameter is required",
                request,
                List.of()
        );
    }

    @ExceptionHandler(PaymentRequestAttachmentBusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleAttachmentBusinessException(
            PaymentRequestAttachmentBusinessException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = switch (exception.getCode()) {
            case "PAYMENT_REQUEST_ATTACHMENT_UPLOAD_FORBIDDEN" ->
                    HttpStatus.FORBIDDEN;
            case "PAYMENT_REQUEST_ATTACHMENT_UPLOAD_STATUS_INVALID" ->
                    HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return errorResponse(
                status,
                exception.getCode(),
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(PaymentRequestAttachmentDeleteException.class)
    public ResponseEntity<ApiErrorResponse> handleAttachmentDeleteException(
            PaymentRequestAttachmentDeleteException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = switch (exception.getCode()) {
            case "PAYMENT_REQUEST_ATTACHMENT_DELETE_FORBIDDEN" ->
                    HttpStatus.FORBIDDEN;
            case "PAYMENT_REQUEST_ATTACHMENT_DELETE_STATUS_INVALID" ->
                    HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return errorResponse(
                status,
                exception.getCode(),
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(AttachmentValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleAttachmentValidationException(
            AttachmentValidationException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = "ATTACHMENT_FILE_TOO_LARGE".equals(
                exception.getCode()
        ) ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
        return errorResponse(
                status,
                exception.getCode(),
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(AttachmentStorageException.class)
    public ResponseEntity<ApiErrorResponse> handleAttachmentStorageException(
            AttachmentStorageException exception,
            HttpServletRequest request
    ) {
        String message = "ATTACHMENT_STORAGE_DELETE_FAILED".equals(
                exception.getCode()
        ) ? "附件檔案暫時無法刪除" : "ATTACHMENT_STORAGE_READ_FAILED".equals(
                exception.getCode()
        ) ? "附件檔案暫時無法讀取" : "Attachment storage operation failed";
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                exception.getCode(),
                message,
                request,
                List.of()
        );
    }

    @ExceptionHandler(PaymentRequestAttachmentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAttachmentNotFound(
            PaymentRequestAttachmentNotFoundException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "PAYMENT_REQUEST_ATTACHMENT_NOT_FOUND",
                "找不到附件",
                request,
                List.of()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "ATTACHMENT_FILE_TOO_LARGE",
                "Attachment file exceeds the configured size limit",
                request,
                List.of()
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestPart(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                "Request body is missing or invalid",
                request,
                List.of()
        );
    }

    @ExceptionHandler(PaymentDraftBusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            PaymentDraftBusinessException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = resolveBusinessStatus(exception.getCode());
        return errorResponse(
                status,
                exception.getCode(),
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(ExpenseTypeAdminBusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleExpenseTypeAdminBusinessException(
            ExpenseTypeAdminBusinessException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = switch (exception.getCode()) {
            case "EXPENSE_TYPE_NOT_FOUND",
                    "EXPENSE_PRICE_SETTING_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.CONFLICT;
        };
        return errorResponse(
                status,
                exception.getCode(),
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(ExpensePriceSettingAdminBusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleExpensePriceSettingAdminBusinessException(
            ExpensePriceSettingAdminBusinessException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = switch (exception.getCode()) {
            case "EXPENSE_PRICE_SETTING_NOT_FOUND", "EXPENSE_TYPE_NOT_FOUND" ->
                    HttpStatus.NOT_FOUND;
            case "EXPENSE_PRICE_BACKDATE_FORBIDDEN",
                    "EXPENSE_PRICE_PERIOD_INVALID",
                    "EXPENSE_PRICE_PRICE_CODE_INVALID",
                    "EXPENSE_PRICE_SETTING_UNSUPPORTED" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
        return errorResponse(
                status,
                exception.getCode(),
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(AuthenticationBusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationBusinessException(
            AuthenticationBusinessException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = "INVALID_CREDENTIALS".equals(exception.getCode())
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.INTERNAL_SERVER_ERROR;
        return errorResponse(
                status,
                exception.getCode(),
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled exception for request path {}",
                request.getRequestURI(),
                exception
        );
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request,
                List.of()
        );
    }

    private HttpStatus resolveBusinessStatus(String code) {
        if ("MANAGER_NOT_AUTHORIZED".equals(code)
                || "PAYMENT_REQUEST_MANAGER_FORBIDDEN".equals(code)
                || "PAYMENT_REQUEST_SUBMIT_FORBIDDEN".equals(code)
                || "PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN".equals(code)) {
            return HttpStatus.FORBIDDEN;
        }
        if ("PAYMENT_REQUEST_LIST_SCOPE_REQUIRED".equals(code)) {
            return HttpStatus.BAD_REQUEST;
        }
        if ("INVALID_CASHIER_ID".equals(code)
                || "INVALID_PAID_BY_ID".equals(code)
                || "INVALID_PAYMENT_DATE".equals(code)
                || "INVALID_EXPORT_PERIOD".equals(code)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (isNotFoundCode(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if (isConflictCode(code)) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private boolean isNotFoundCode(String code) {
        return switch (code) {
            case "APPLICANT_NOT_FOUND",
                    "COMPANY_NOT_FOUND",
                    "CUSTOMER_NOT_FOUND",
                    "EXPENSE_TYPE_NOT_FOUND",
                    "PRICE_SETTING_NOT_FOUND",
                    "EXPENSE_PRICE_SETTING_NOT_FOUND",
                    "CASHIER_NOT_FOUND",
                    "PAID_BY_NOT_FOUND",
                    "PAYMENT_REQUEST_NOT_FOUND" -> true;
            default -> false;
        };
    }

    private boolean isConflictCode(String code) {
        return switch (code) {
            case "APPLICANT_INACTIVE",
                    "APPLICANT_DEPARTMENT_MISSING",
                    "DEPARTMENT_INACTIVE",
                    "COMPANY_INACTIVE",
                    "CUSTOMER_INACTIVE",
                    "EXPENSE_TYPE_INACTIVE",
                    "CUSTOMER_CATEGORY_MISMATCH",
                    "PRICE_SETTING_CONFLICT",
                    "PAYMENT_REQUEST_VERSION_CONFLICT",
                    "PAYMENT_REQUEST_NOT_DRAFT",
                    "PAYMENT_REQUEST_DEPARTMENT_MISSING",
                    "PAYMENT_REQUEST_NOT_PENDING_MANAGER",
                    "PAYMENT_REQUEST_NOT_PENDING_CASHIER",
                    "PAYMENT_REQUEST_NOT_APPROVED",
                    "PAYMENT_REQUEST_ALREADY_PAID",
                    "PAYMENT_PROOF_ALREADY_EXISTS",
                    "CASHIER_INACTIVE",
                    "PAID_BY_INACTIVE",
                    "SUPERVISOR_SNAPSHOT_MISSING",
                    "SUPERVISOR_NOT_FOUND",
                    "SUPERVISOR_CONFLICT",
                    "SUPERVISOR_INACTIVE" -> true;
            default -> false;
        };
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<FieldValidationError> fieldErrors
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                OffsetDateTime.now(BUSINESS_ZONE),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(status).body(response);
    }
}
