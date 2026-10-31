package com.souhajaidi.wallet.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns every exception into an RFC 7807 problem document with a stable error code.
 *
 * <p>Two rules govern this class. Known business failures return their own message,
 * because the caller needs it to act. Everything else returns a fixed generic message
 * and is logged server-side with its stack trace, because an unexpected exception's
 * message can carry table names, SQL fragments or file paths.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI ERROR_TYPE = URI.create("https://github.com/Souhajaidi1/fintech-wallet-engine/errors");

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        if (ex.status().is5xxServerError()) {
            log.error("Business invariant violated on {} {}", request.getMethod(), request.getRequestURI(), ex);
        }
        return problem(ex.status(), ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> violations = new TreeMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> violations.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem =
                problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid.", request);
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        // The message is intentionally the same for an unknown email and a wrong
        // password, so the response cannot be used to enumerate accounts.
        log.info("Authentication rejected on {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
        return problem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Invalid credentials.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You may not perform this action.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "The request could not be completed.",
                request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(ERROR_TYPE);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("code", code);
        extras.put("timestamp", Instant.now().toString());
        extras.forEach(problem::setProperty);
        return problem;
    }
}
