package com.unispace.identity.api;

import com.unispace.identity.application.AuthenticatedSession;
import com.unispace.identity.application.AuthenticationStateService;
import com.unispace.identity.application.AuthenticationStateService.RegistrationFlow;
import com.unispace.identity.application.DeviceAuthenticationService;
import com.unispace.identity.application.DeviceAuthenticationService.Rejected;
import com.unispace.identity.application.OnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping(path="/api/v1/auth/device-challenges", consumes=MediaType.APPLICATION_JSON_VALUE)
public class DeviceAuthenticationController {
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final int MAX_BODY = 4096;
    private final DeviceAuthenticationService devices;

    public DeviceAuthenticationController(DeviceAuthenticationService devices) {
        this.devices = devices;
    }

    @PostMapping
    ResponseEntity<Map<String,Object>> start(@AuthenticationPrincipal RegistrationFlow flow, HttpServletRequest request) {
        var body = read(request, Set.of(), Set.of("deviceId"));
        String device = body.has("deviceId") ? text(body, "deviceId", 128) : null;
        return response(HttpStatus.CREATED).body(devices.start(flow, device));
    }

    @PostMapping("/{id}/verify-otp")
    ResponseEntity<Map<String,Object>> otp(@AuthenticationPrincipal RegistrationFlow flow,
                                          @PathVariable String id, HttpServletRequest request) {
        var body = read(request, Set.of("code"), Set.of("code"));
        return response(HttpStatus.OK).body(devices.verifyOtp(flow, id, text(body, "code", 128)));
    }

    @PostMapping("/{id}/register-key")
    ResponseEntity<Map<String,Object>> register(@AuthenticationPrincipal RegistrationFlow flow,
                                               @PathVariable String id, HttpServletRequest request) {
        var body = read(request, Set.of("publicKeyJwk"), Set.of("publicKeyJwk", "deviceDescription"));
        if (body.has("deviceDescription")) {
            var description = body.get("deviceDescription");
            if (!description.isString() || description.asString().length() > 160) throw new InvalidRequest();
        }
        var jwk = body.get("publicKeyJwk");
        if (!jwk.isObject()) throw new Rejected(DeviceAuthenticationService.Failure.DEVICE_KEY_INVALID);
        var parts = new HashMap<String, Object>();
        jwk.properties().forEach(field -> parts.put(field.getKey(),
                field.getValue().isString() ? field.getValue().asString() : field.getValue()));
        return response(HttpStatus.OK).body(devices.register(flow, id, parts));
    }

    @PostMapping("/{id}/verify-proof")
    ResponseEntity<AuthenticatedSession> proof(@AuthenticationPrincipal RegistrationFlow flow,
                                                              @PathVariable String id, HttpServletRequest request) {
        var body = read(request, Set.of("signature"), Set.of("signature"));
        return response(HttpStatus.OK).body(devices.verifyProof(flow, id, text(body, "signature", 128)));
    }

    private static JsonNode read(HttpServletRequest request, Set<String> required, Set<String> allowed) {
        try {
            byte[] bytes = request.getInputStream().readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) throw new InvalidRequest();
            var body = JSON.readTree(bytes);
            if (body == null || !body.isObject() || !allowed.containsAll(body.propertyNames())
                    || !body.propertyNames().containsAll(required)) throw new InvalidRequest();
            return body;
        } catch (IOException | JacksonException e) {
            throw new InvalidRequest();
        }
    }

    private static String text(JsonNode body, String key, int limit) {
        var node = body.get(key);
        if (!node.isString() || node.asString().isBlank() || node.asString().length() > limit) throw new InvalidRequest();
        return node.asString();
    }

    @ExceptionHandler(Rejected.class)
    ResponseEntity<Map<String,Object>> rejected(Rejected e) {
        HttpStatus status = switch (e.failure()) {
            case AUTH_FLOW_EXPIRED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED, DEVICE_VERIFICATION_REQUIRED, DEVICE_TRUST_REVOKED -> HttpStatus.FORBIDDEN;
            case DEVICE_KEY_CONFLICT -> HttpStatus.CONFLICT;
            case OTP_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case EMAIL_SERVICE_UNAVAILABLE, AUTH_STATE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return problem(status, e.failure().name());
    }

    @ExceptionHandler(InvalidRequest.class)
    ResponseEntity<Map<String,Object>> invalid() {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    @ExceptionHandler(AuthenticationStateService.DisabledAccountException.class)
    ResponseEntity<Map<String,Object>> disabled() {
        return problem(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");
    }

    @ExceptionHandler(OnboardingService.Rejected.class)
    ResponseEntity<Map<String,Object>> missingOtpKey() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_SERVICE_UNAVAILABLE");
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
}
