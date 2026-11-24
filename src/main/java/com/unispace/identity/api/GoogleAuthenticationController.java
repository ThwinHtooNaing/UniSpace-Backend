package com.unispace.identity.api;

import com.unispace.identity.application.AuthenticationStateService;
import com.unispace.identity.application.GoogleSignInService;
import com.unispace.identity.application.GoogleSignInService.Failure;
import com.unispace.identity.application.GoogleSignInService.Rejected;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping(path = "/api/v1/auth", consumes = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "unispace.auth.google", name = "audience")
public class GoogleAuthenticationController {
    private static final int MAX_BODY_BYTES = 24 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final GoogleSignInService signIn;

    public GoogleAuthenticationController(GoogleSignInService signIn) {
        this.signIn = signIn;
    }

    @PostMapping("/challenges")
    ResponseEntity<GoogleSignInService.ChallengeResponse> challenge(HttpServletRequest request, Authentication authentication) {
        var body = body(request, Set.of("provider", "purpose"));
        requireGoogle(body);
        String purpose = body.has("purpose") ? requiredText(body, "purpose", 16) : "SIGN_IN";
        var principal = authentication == null ? null : authentication.getPrincipal();
        var bound = switch (purpose) {
            case "SIGN_IN" -> null;
            case "LINK_PROVIDER" -> {
                if (principal == null) throw new Rejected(Failure.AUTHENTICATION_REQUIRED);
                if (!(principal instanceof AuthenticationStateService.AccessSession access)) {
                    throw new Rejected(Failure.ACCESS_DENIED);
                }
                yield access;
            }
            default -> throw new InvalidRequest();
        };
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(signIn.challenge(bound));
    }

    @PostMapping("/provider-exchange")
    ResponseEntity<GoogleSignInService.FlowResponse> exchange(HttpServletRequest request) {
        var body = body(request, Set.of("provider", "challengeId", "idToken"));
        requireGoogle(body);
        String challengeId = requiredText(body, "challengeId", 128);
        String token = requiredText(body, "idToken", 16 * 1024);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(signIn.exchange(challengeId, token));
    }

    private static JsonNode body(HttpServletRequest request, Set<String> fields) {
        try {
            byte[] input = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (input.length > MAX_BODY_BYTES) throw new InvalidRequest();
            var node = JSON.readTree(input);
            if (node == null || !node.isObject() || !fields.containsAll(node.propertyNames())) throw new InvalidRequest();
            return node;
        } catch (IOException | tools.jackson.core.JacksonException e) {
            throw new InvalidRequest();
        }
    }

    private static void requireGoogle(JsonNode body) {
        String provider = requiredText(body, "provider", 16);
        if ("APPLE".equals(provider)) throw new Rejected(Failure.AUTH_PROVIDER_UNAVAILABLE);
        if (!"GOOGLE".equals(provider)) throw new InvalidRequest();
    }

    private static String requiredText(JsonNode body, String field, int max) {
        var node = body.get(field);
        if (node == null || !node.isString()) throw new InvalidRequest();
        String value = node.asString();
        if (value.isBlank() || value.length() > max) throw new InvalidRequest();
        return value;
    }

    @ExceptionHandler(Rejected.class)
    ResponseEntity<Map<String, Object>> rejected(Rejected error) {
        return problem(switch (error.failure()) {
            case ACCESS_DENIED, ACCOUNT_DISABLED -> HttpStatus.FORBIDDEN;
            case AUTH_PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.UNAUTHORIZED;
        }, error.failure().name());
    }

    @ExceptionHandler(InvalidRequest.class)
    ResponseEntity<Map<String, Object>> invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class, IllegalStateException.class})
    ResponseEntity<Map<String, Object>> unavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, Failure.AUTH_PROVIDER_UNAVAILABLE.name());
    }

    @ExceptionHandler(AuthenticationStateService.DisabledAccountException.class)
    ResponseEntity<Map<String, Object>> disabled() {
        return problem(HttpStatus.FORBIDDEN, Failure.ACCOUNT_DISABLED.name());
    }

    private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String code) {
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (status == HttpStatus.UNAUTHORIZED) response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return response.body(Map.of("type", "about:blank", "title", status.getReasonPhrase(), "status", status.value(),
                "code", code, "traceId", UUID.randomUUID().toString()));
    }

    private static final class InvalidRequest extends RuntimeException {}
}
