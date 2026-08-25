package io.github.ahmasm.vending.machine.web;

import io.github.ahmasm.vending.machine.application.command.IdempotencyKeyReusedException;
import io.github.ahmasm.vending.machine.application.command.MachineNotFoundException;
import io.github.ahmasm.vending.machine.application.money.CurrencyRejectedException;
import io.github.ahmasm.vending.machine.application.money.CurrencyValidationUnavailableException;
import io.github.ahmasm.vending.machine.application.purchase.PurchaseNotFoundException;
import io.github.ahmasm.vending.machine.application.session.SessionNotFoundException;
import io.github.ahmasm.vending.machine.domain.machine.ActiveSessionAlreadyExistsException;
import io.github.ahmasm.vending.machine.domain.machine.ActiveSessionNotFoundException;
import io.github.ahmasm.vending.machine.domain.machine.ChangeUnavailableException;
import io.github.ahmasm.vending.machine.domain.machine.InsufficientBalanceException;
import io.github.ahmasm.vending.machine.domain.machine.ProductOutOfStockException;
import io.github.ahmasm.vending.machine.domain.machine.SlotNotFoundException;
import io.github.ahmasm.vending.machine.domain.money.NegativeMoneyAmountException;
import io.github.ahmasm.vending.machine.domain.money.UnsupportedDenominationException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public final class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MachineNotFoundException.class)
    ResponseEntity<ProblemDetail> handleMachineNotFound(
            MachineNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Machine not found",
                "MACHINE_NOT_FOUND",
                "The requested vending machine does not exist",
                request);
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<ProblemDetail> handleSessionNotFound(
            SessionNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Session not found",
                "SESSION_NOT_FOUND",
                "The requested session does not exist for this vending machine",
                request);
    }

    @ExceptionHandler(PurchaseNotFoundException.class)
    ResponseEntity<ProblemDetail> handlePurchaseNotFound(
            PurchaseNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Purchase not found",
                "PURCHASE_NOT_FOUND",
                "The requested purchase does not exist for this vending machine",
                request);
    }

    @ExceptionHandler(ActiveSessionAlreadyExistsException.class)
    ResponseEntity<ProblemDetail> handleActiveSessionAlreadyExists(
            ActiveSessionAlreadyExistsException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Active session already exists",
                "ACTIVE_SESSION_ALREADY_EXISTS",
                "The vending machine already has an active session",
                request);
    }

    @ExceptionHandler(ActiveSessionNotFoundException.class)
    ResponseEntity<ProblemDetail> handleActiveSessionNotFound(
            ActiveSessionNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Active session not found",
                "ACTIVE_SESSION_NOT_FOUND",
                "The requested active session does not exist",
                request);
    }

    @ExceptionHandler(SlotNotFoundException.class)
    ResponseEntity<ProblemDetail> handleSlotNotFound(
            SlotNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Slot not found",
                "SLOT_NOT_FOUND",
                "The selected slot does not exist",
                request,
                Map.of("slotCode", exception.slotCode().value()));
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    ResponseEntity<ProblemDetail> handleInsufficientBalance(
            InsufficientBalanceException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Insufficient balance",
                "INSUFFICIENT_BALANCE",
                "Current balance is not sufficient for the selected product",
                request,
                Map.of(
                        "balance", exception.balance().amount(),
                        "productPrice", exception.price().amount()));
    }

    @ExceptionHandler(ProductOutOfStockException.class)
    ResponseEntity<ProblemDetail> handleProductOutOfStock(
            ProductOutOfStockException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Product out of stock",
                "PRODUCT_OUT_OF_STOCK",
                "The selected product is out of stock",
                request,
                Map.of("slotCode", exception.slotCode().value()));
    }

    @ExceptionHandler(ChangeUnavailableException.class)
    ResponseEntity<ProblemDetail> handleChangeUnavailable(
            ChangeUnavailableException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Exact change unavailable",
                "CHANGE_UNAVAILABLE",
                "Exact change cannot be returned for this purchase",
                request,
                Map.of("changeDue", exception.changeDue().amount()));
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<ProblemDetail> handleIdempotencyKeyReused(
            IdempotencyKeyReusedException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Idempotency conflict",
                "IDEMPOTENCY_KEY_REUSED",
                "The idempotency key was already used for a different command",
                request);
    }

    @ExceptionHandler(CurrencyRejectedException.class)
    ResponseEntity<ProblemDetail> handleCurrencyRejected(
            CurrencyRejectedException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Currency rejected",
                "CURRENCY_REJECTED",
                "The inserted money was rejected",
                request,
                Map.of("reason", exception.reason().name()));
    }

    @ExceptionHandler(CurrencyValidationUnavailableException.class)
    ResponseEntity<ProblemDetail> handleCurrencyValidationUnavailable(
            CurrencyValidationUnavailableException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Currency validation unavailable",
                "CURRENCY_VALIDATION_UNAVAILABLE",
                "Currency validation is temporarily unavailable",
                request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ProblemDetail> handleConcurrentModification(
            Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Concurrent modification",
                "CONCURRENT_MODIFICATION",
                "The vending machine changed concurrently; retry the command",
                request);
    }

    @ExceptionHandler({
        UnsupportedDenominationException.class,
        NegativeMoneyAmountException.class,
        ConstraintViolationException.class
    })
    ResponseEntity<ProblemDetail> handleInvalidRequest(
            Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "INVALID_REQUEST",
                "Request validation failed",
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpectedFailure(
            Exception exception, HttpServletRequest request) {
        var correlationId = CorrelationIdFilter.correlationId(request);
        LOGGER.error("Unhandled request failure correlationId={}", correlationId, exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal error",
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request,
                correlationId);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest webRequest) {
        var request = ((ServletWebRequest) webRequest).getRequest();
        var status = HttpStatus.resolve(statusCode.value());
        if (status == null || status.is5xxServerError()) {
            var correlationId = CorrelationIdFilter.correlationId(request);
            LOGGER.error("Unhandled request failure correlationId={}", correlationId, exception);
            var response = problem(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal error",
                    "INTERNAL_ERROR",
                    "An unexpected error occurred",
                    request,
                    correlationId);
            return new ResponseEntity<>(
                    response.getBody(), response.getHeaders(), response.getStatusCode());
        }

        var response = problem(
                status,
                status == HttpStatus.BAD_REQUEST ? "Invalid request" : status.getReasonPhrase(),
                frameworkErrorCode(status),
                status == HttpStatus.BAD_REQUEST
                        ? "Request validation failed"
                        : "The request could not be processed",
                request);
        return new ResponseEntity<>(response.getBody(), response.getHeaders(), response.getStatusCode());
    }

    private static String frameworkErrorCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case NOT_ACCEPTABLE -> "NOT_ACCEPTABLE";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "HTTP_" + status.value();
        };
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String code,
            String detail,
            HttpServletRequest request) {
        return problem(
                status,
                title,
                code,
                detail,
                request,
                CorrelationIdFilter.correlationId(request),
                Map.of());
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String code,
            String detail,
            HttpServletRequest request,
            Map<String, ?> properties) {
        return problem(
                status,
                title,
                code,
                detail,
                request,
                CorrelationIdFilter.correlationId(request),
                properties);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String code,
            String detail,
            HttpServletRequest request,
            String correlationId) {
        return problem(status, title, code, detail, request, correlationId, Map.of());
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String code,
            String detail,
            HttpServletRequest request,
            String correlationId,
            Map<String, ?> properties) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:vending-machine:problem:"
                + code.toLowerCase().replace('_', '-')));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId);
        properties.forEach(problem::setProperty);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, headers, status);
    }
}
