package com.unispace.identity.api;

import com.unispace.identity.application.AuthenticatedSession;
import com.unispace.identity.application.AuthenticationStateService;
import com.unispace.identity.application.AuthenticationStateService.AccessSession;
import com.unispace.identity.application.AuthenticationStateService.RegistrationFlow;
import com.unispace.identity.application.SessionLifecycleService;
import com.unispace.identity.application.SessionLifecycleService.Rejected;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1/auth")
public class SessionLifecycleController {
    private static final int MAX_BODY = 2048;
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final SessionLifecycleService sessions;

    public SessionLifecycleController(SessionLifecycleService sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthenticatedSession> refresh(HttpServletRequest request, @AuthenticationPrincipal Object principal) {
        requireNonFlow(principal);
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) throw new InvalidRequest();
        String credential = refreshBody(request);
        if (credential == null) throw new InvalidRequest();
        return response(HttpStatus.OK).body(sessions.refresh(credential));
    }

    @PostMapping("/sign-out")
    ResponseEntity<Void> signOut(HttpServletRequest request, @AuthenticationPrincipal Object principal) {
        requireNonFlow(principal);
        String credential = refreshBody(request);
        if (principal instanceof AccessSession bearer) {
            // The candidate defines two separate messages and leaves mixed-credential precedence unresolved.
            if (credential != null) throw new InvalidRequest();
            sessions.signOut(bearer);
        } else {
            if (request.getHeader(HttpHeaders.AUTHORIZATION) != null || credential == null) {
                throw new Rejected(SessionLifecycleService.Failure.AUTHENTICATION_REQUIRED);
            }
            sessions.signOutWithRefresh(credential);
        }
        return response(HttpStatus.NO_CONTENT).build();
    }

    private static void requireNonFlow(Object principal) {
        if (principal instanceof RegistrationFlow) throw new FlowDenied();
    }

    private static String refreshBody(HttpServletRequest request) {
        try {
            byte[] bytes = request.getInputStream().readNBytes(MAX_BODY + 1);
            if (bytes.length == 0) return null;
            if (bytes.length > MAX_BODY || request.getContentType() == null
                    || !MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(request.getContentType()))) {
                throw new InvalidRequest();
            }
            var body = JSON.readTree(bytes);
            if (body == null || !body.isObject() || !body.propertyNames().equals(Set.of("refreshToken"))
                    || !body.get("refreshToken").isString() || body.get("refreshToken").asString().isBlank()) {
                throw new InvalidRequest();
            }
            return body.get("refreshToken").asString();
        } catch (IOException | JacksonException | IllegalArgumentException e) {
            throw new InvalidRequest();
        }
    }

    @ExceptionHandler(Rejected.class)
    ResponseEntity<Map<String,Object>> rejected(Rejected e) {
        return problem(HttpStatus.UNAUTHORIZED, e.failure().name());
    }

    @ExceptionHandler(InvalidRequest.class)
    ResponseEntity<Map<String,Object>> invalid() {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    @ExceptionHandler(FlowDenied.class)
    ResponseEntity<Map<String,Object>> flowDenied() {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @ExceptionHandler(AuthenticationStateService.DisabledAccountException.class)
    ResponseEntity<Map<String,Object>> disabled() {
        return problem(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class, IllegalStateException.class})
    ResponseEntity<Map<String,Object>> unavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_STATE_UNAVAILABLE");
    }

    private static ResponseEntity.BodyBuilder response(HttpStatus status) {
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private static ResponseEntity<Map<String,Object>> problem(HttpStatus status, String code) {
        var response = response(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (status == HttpStatus.UNAUTHORIZED) response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return response.body(Map.of("type","about:blank","title",status.getReasonPhrase(),"status",status.value(),
                "code",code,"traceId",UUID.randomUUID().toString()));
    }

    private static final class InvalidRequest extends RuntimeException {}
    private static final class FlowDenied extends RuntimeException {}
}
