package com.mrsrik.ai_powered_commerce.common.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every exception in the design's "Exception mapping" table to the uniform
 * error body.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    static final String INTERNAL_ERROR_MESSAGE = "An unexpected error occurred";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Comparator<FieldErrorDetail> BY_FIELD_THEN_MESSAGE = Comparator
            .comparing(FieldErrorDetail::field, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(FieldErrorDetail::message, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final ErrorResponses errors;

    public GlobalExceptionHandler(ErrorResponses errors) {
        this.errors = errors;
    }

    /** Domain exceptions carry their own status, code and message. */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), null, null);
    }

    /** Invalid {@code @Valid} request body: 400 listing every invalid field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        return validationFailed(fromErrors(ex.getBindingResult()));
    }

    /**
     * Invalid validated method parameters (Spring 6.1+): 400 listing every
     * violation.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorDetail> details = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                details.addAll(fromErrors(parameterErrors));
            } else {
                String name = result.getMethodParameter().getParameterName();
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    details.add(new FieldErrorDetail(name, constraintOf(error), error.getDefaultMessage()));
                }
            }
        }
        return validationFailed(details);
    }

    /**
     * Bean Validation failure outside the web binder: 400 listing every violation.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> details = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            details.add(new FieldErrorDetail(leafName(violation.getPropertyPath()),
                    violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                    violation.getMessage()));
        }
        return validationFailed(details);
    }

    /**
     * Malformed UUID or enum in a path or query value: 400 without echoing the
     * value.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_IDENTIFIER,
                "Invalid value for parameter '" + ex.getName() + "'", null, null);
    }

    /** Missing required query parameter: 400 with the parameter name only. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Missing required parameter '" + ex.getParameterName() + "'", null, null);
    }

    /**
     * Unreadable request body: 400 with a generic message and no parser details.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Request body is missing or malformed", null, null);
    }

    /** Unsupported Content-Type: 415, keeping the Accept header Spring computes. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Content type is not supported", ex.getHeaders(), null);
    }

    /** Wrong HTTP method: 405, keeping the Allow header Spring computes. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method is not allowed for this route", ex.getHeaders(), null);
    }

    /**
     * Unknown route: Spring 7 raises {@link NoResourceFoundException} when no
     * handler matches.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.ROUTE_NOT_FOUND, "Route not found", null, null);
    }

    /**
     * Method-security denial for an authenticated user without the role: 403, never
     * a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Access is denied", null, null);
    }

    /**
     * Catch-all: 500 with a fixed message; the cause is logged only server-side.
     * This includes
     * {@code DataIntegrityViolationException}, which the design translates inside
     * specific services only.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE, null,
                null);
    }

    private ResponseEntity<ErrorResponse> validationFailed(List<FieldErrorDetail> details) {
        List<FieldErrorDetail> sorted = new ArrayList<>(details);
        sorted.sort(BY_FIELD_THEN_MESSAGE);
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", null, sorted);
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorCode code, String message,
            HttpHeaders headers, List<FieldErrorDetail> fieldErrors) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
        if (headers != null) {
            builder.headers(headers);
        }
        return builder.body(errors.build(status.value(), code, message, fieldErrors));
    }

    private static List<FieldErrorDetail> fromErrors(Errors result) {
        List<FieldErrorDetail> details = new ArrayList<>();
        for (FieldError error : result.getFieldErrors()) {
            details.add(new FieldErrorDetail(error.getField(), constraintOf(error), error.getDefaultMessage()));
        }
        for (ObjectError error : result.getGlobalErrors()) {
            details.add(new FieldErrorDetail(error.getObjectName(), constraintOf(error), error.getDefaultMessage()));
        }
        return details;
    }

    /**
     * The constraint is the last (least specific) message code, e.g.
     * {@code NotBlank}.
     */
    private static String constraintOf(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        return codes == null || codes.length == 0 ? null : codes[codes.length - 1];
    }

    private static String leafName(Path path) {
        String name = null;
        for (Path.Node node : path) {
            name = node.getName();
        }
        return name;
    }
}
