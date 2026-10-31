package com.souhajaidi.wallet.iam.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers an unauthenticated request with 401 and a problem document.
 *
 * <p>Without this, Spring Security's default entry point for a chain with no form login
 * and no HTTP Basic returns 403, which tells the caller "you are not allowed" when the
 * truth is "you did not identify yourself" — two different problems with two different
 * fixes on the client side.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        problem.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "AUTHENTICATION_REQUIRED");
        problem.setProperty("timestamp", Instant.now().toString());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
