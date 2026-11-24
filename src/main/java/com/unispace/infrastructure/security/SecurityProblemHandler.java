package com.unispace.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

final class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    static final String STATE_UNAVAILABLE = "AUTH_STATE_UNAVAILABLE";
    private final ObjectMapper mapper;

    SecurityProblemHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        if (exception instanceof OAuth2AuthenticationException oauth) {
            String code = oauth.getError().getErrorCode();
            if (STATE_UNAVAILABLE.equals(code)) {
                write(response, HttpStatus.SERVICE_UNAVAILABLE, code);
                return;
            }
            if (ACCOUNT_DISABLED.equals(code)) {
                write(response, HttpStatus.FORBIDDEN, code);
                return;
            }
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    private void write(HttpServletResponse response, HttpStatus status, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        mapper.writeValue(response.getOutputStream(), Map.of("type", "about:blank", "title", status.getReasonPhrase(),
                "status", status.value(), "code", code, "traceId", UUID.randomUUID().toString()));
    }
}
